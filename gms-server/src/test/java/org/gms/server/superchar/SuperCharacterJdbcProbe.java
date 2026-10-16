package org.gms.server.superchar;

import com.alibaba.fastjson2.JSONObject;
import org.gms.client.Character;
import org.gms.client.Skill;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.model.pojo.SkillEntry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs only against the disposable schema created by Verify-SuperCharacter.ps1. */
public final class SuperCharacterJdbcProbe {
    private static Connection open() throws SQLException {
        String url=System.getenv("SUPERCHAR_JDBC");
        if(url==null || !url.contains("/codex_superchar_check?"))throw new IllegalArgumentException("isolated-schema-required");
        return DriverManager.getConnection(url,System.getenv("SUPERCHAR_USER"),System.getenv("SUPERCHAR_PASSWORD"));
    }
    private static Connection fault(String point) throws SQLException {
        Connection real=open();
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                if(point.equals("skills") && sql.startsWith("INSERT INTO skills"))throw new SQLException("injected-skills");
                if(point.equals("items") && sql.startsWith("INSERT INTO `inventoryitems`"))throw new SQLException("injected-items");
                if(point.equals("audit") && sql.startsWith("INSERT INTO superchar_audit"))throw new SQLException("injected-audit");
            }
            if(method.getName().equals("commit") && point.equals("uncommitted"))throw new SQLException("injected-commit");
            try {
                Object result=method.invoke(real,args);
                if(method.getName().equals("commit") && point.equals("committed"))throw new SQLException("lost-commit-response");
                return result;
            } catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    private static JSONObject snapshot() throws SQLException {
        try(Connection con=open()){return SuperCharacterStore.snapshot(con,123);}
    }
    private static Character fixture() throws Exception {
        Method method=SuperCharacterTest.class.getDeclaredMethod("player");
        method.setAccessible(true);
        Character chr=(Character)method.invoke(new SuperCharacterTest());
        when(chr.getLevel()).thenReturn(50); when(chr.getStr()).thenReturn(50); when(chr.getDex()).thenReturn(10);
        when(chr.getInt()).thenReturn(4); when(chr.getLuk()).thenReturn(4);
        when(chr.getHp()).thenReturn(100); when(chr.getMaxHp()).thenReturn(100);
        when(chr.getMp()).thenReturn(50); when(chr.getMaxMp()).thenReturn(50);
        Equip original=new Equip(1002357,(short)8); original.setStr((short)15); original.setOwner("probe-original");
        chr.getInventory(InventoryType.EQUIP).addItemFromDB(original);
        chr.getInventory(InventoryType.USE).addItemFromDB(new Item(2000005,(short)3,(short)5));
        return chr;
    }
    private static SuperCharacterPlan plan() {
        var options=SuperCharacterPolicy.parse(new String[]{"darkknight","confirm"});
        Equip added=new Equip(1432047,(short)1); added.setWatk((short)200);
        Skill skill=mock(Skill.class); when(skill.getId()).thenReturn(1321003);
        return new SuperCharacterPlan(options,200,SuperCharacterPolicy.attributes(options.career(),32767),
            Map.of(skill,new SkillEntry((byte)30,30,-1)),List.of(added),1,0,0,1,0,0,95,false);
    }
    public static void main(String[] args) throws Exception {
        Method bootstrap=Class.forName("org.gms.service.OfflineGrantServiceTest").getDeclaredMethod("bootstrapMessages");
        bootstrap.setAccessible(true); bootstrap.invoke(null);
        Path directory=Path.of("/tmp/codex-superchar-probe-backups");
        Files.createDirectories(directory);
        try(Connection con=open();PreparedStatement ps=con.prepareStatement("INSERT INTO characters (id,accountid,name,gm,job,level,str,dex,`int`,luk,hp,mp,maxhp,maxmp,equipslots,useslots,setupslots,etcslots) VALUES (123,456,'SCProbe',6,132,50,50,10,4,4,100,50,100,50,24,24,24,24)")){ps.executeUpdate();}
        try(Connection con=open();PreparedStatement ps=con.prepareStatement("INSERT INTO accounts (id,name,password,loggedin) VALUES (456,'SCProbe','disabled-test',0)")){ps.executeUpdate();}
        for(String point:List.of("skills","items","audit","uncommitted")) {
            Character chr=fixture(); JSONObject before=snapshot();
            synchronized(chr) {
                assertThrows(IllegalStateException.class,()->new SuperCharacterStore(()->fault(point),directory).execute(chr,plan()));
            }
            assertTrue(SuperCharacterRestore.unchanged(snapshot(),before),"rollback: "+point);
            verify(chr,never()).applyCommittedSuperCharacter(any());
            System.out.println("PASS real JDBC rollback: "+point);
        }
        Character chr=fixture(); SuperCharacterStore.Result result;
        synchronized(chr){result=new SuperCharacterStore(SuperCharacterJdbcProbe::open,directory).execute(chr,plan());}
        String backupId=result.backupId();
        JSONObject after=snapshot(); JSONObject finalCharacter=after.getJSONArray("character").getJSONObject(0);
        assertEquals(200,finalCharacter.getIntValue("level")); assertEquals(32767,finalCharacter.getIntValue("str"));
        assertEquals(30000,finalCharacter.getIntValue("maxhp")); assertEquals(30000,finalCharacter.getIntValue("hp"));
        assertEquals(30000,finalCharacter.getIntValue("maxmp")); assertEquals(30000,finalCharacter.getIntValue("mp"));
        for(String field:List.of("equipslots","useslots","setupslots","etcslots"))assertEquals(96,finalCharacter.getIntValue(field));
        assertEquals(3,after.getJSONArray("items").size()); assertEquals(2,after.getJSONArray("equipment").size());
        assertEquals(1,after.getJSONArray("skills").size());
        System.out.println("PASS real JDBC committed character, skills, equipment, five inventory capacities");
        try(Connection con=open();PreparedStatement ps=con.prepareStatement("UPDATE inventoryitems SET position=9 WHERE characterid=123 AND itemid=1002357")){ps.executeUpdate();}
        try(Connection con=open()) {
            assertThrows(IllegalArgumentException.class,()->SuperCharacterRestore.restore(con,backupId,directory));
        }
        System.out.println("PASS restore refuses changed inventory");
        try(Connection con=open();PreparedStatement ps=con.prepareStatement("UPDATE inventoryitems SET position=8 WHERE characterid=123 AND itemid=1002357")){ps.executeUpdate();}
        try(Connection con=open()) {
            String restore=SuperCharacterRestore.restore(con,backupId,directory);
            assertNotNull(restore);
        }
        JSONObject restored=snapshot();
        assertEquals(50,restored.getJSONArray("character").getJSONObject(0).getIntValue("level"));
        assertEquals(24,restored.getJSONArray("character").getJSONObject(0).getIntValue("equipslots"));
        assertEquals(2,restored.getJSONArray("items").size()); assertEquals(1,restored.getJSONArray("equipment").size());
        System.out.println("PASS bound offline restore with pre-restore backup and transaction audit");
        try(Connection con=open()){assertThrows(IllegalArgumentException.class,()->SuperCharacterRestore.restore(con,backupId,directory));}
        System.out.println("PASS repeated restore refused");
        chr=fixture(); Character finalChr=chr;
        final int[] connections={0};
        synchronized(chr){result=new SuperCharacterStore(()->connections[0]++==0?fault("committed"):open(),directory).execute(chr,plan());}
        assertFalse(result.reload()); verify(finalChr,times(1)).applyCommittedSuperCharacter(any());
        assertEquals(3,snapshot().getJSONArray("items").size());
        System.out.println("PASS lost commit response resolved by durable audit; no duplicate grant");
        System.out.println("ALL ISOLATED DATABASE CHECKS PASSED");
    }
}
