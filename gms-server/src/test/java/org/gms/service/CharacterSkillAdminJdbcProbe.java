package org.gms.service;

import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.exception.BizException;
import org.gms.util.DatabaseConnection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CharacterSkillAdminJdbcProbe {
    private static Connection open() throws SQLException {
        String url=System.getenv("SUPERCHAR_JDBC");
        if(url==null || !url.contains("/codex_skill_admin_check_"))throw new IllegalArgumentException("isolated-schema-required");
        return DriverManager.getConnection(url,System.getenv("SUPERCHAR_USER"),System.getenv("SUPERCHAR_PASSWORD"));
    }
    private static void sql(String query) throws SQLException {try(Connection con=open();var ps=con.prepareStatement(query)){ps.executeUpdate();}}
    private static long count(String query) throws SQLException {try(Connection con=open();var ps=con.prepareStatement(query);var rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    private static Connection connection(boolean failAudit) throws SQLException {
        Connection real=open();
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(failAudit && method.getName().equals("prepareStatement") && args[0] instanceof String query && query.startsWith("INSERT INTO admin_skill_edits"))throw new SQLException("injected-audit");
            try{return method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    private static CharacterSkillAdminService.Edit request(CharacterSkillAdminService service,int id,String action,int level,int master,boolean cross) throws SQLException {
        return new CharacterSkillAdminService.Edit(123,(String)service.list(123).get("revision"),id,action,level,master,cross,null);
    }
    private static CharacterSkillAdminService.Edit confirm(CharacterSkillAdminService.Edit edit,Map<String,Object> preview) {
        return new CharacterSkillAdminService.Edit(edit.cid(),edit.revision(),edit.skillId(),edit.action(),edit.level(),edit.masterLevel(),edit.allowCross(),(String)preview.get("previewHash"));
    }
    public static void main(String[] args) throws Exception {
        OfflineGrantServiceTest.bootstrapMessages();
        sql("INSERT INTO accounts(id,name,password,loggedin,banned) VALUES(456,'SkillProbe','disabled',0,1)");
        sql("INSERT INTO characters(id,accountid,name,job,gm) VALUES(123,456,'SkillProbe',132,6)");
        sql("INSERT INTO skills(characterid,skillid,skilllevel,masterlevel,expiration) VALUES(123,1321000,1,10,123456789)");
        var characters=mock(CharacterAdminService.class);
        when(characters.detail(123)).thenReturn(Map.of("editable",true,"values",Map.of("accountid",456,"job",132)));
        var service=new CharacterSkillAdminService(characters);
        var skill=mock(Skill.class);when(skill.getMaxLevel()).thenReturn(30);when(skill.isFourthJob()).thenReturn(true);
        var failAudit=new AtomicReference<>(false);
        try(var db=mockStatic(DatabaseConnection.class);var factory=mockStatic(SkillFactory.class)) {
            db.when(DatabaseConnection::getConnection).thenAnswer(call->connection(failAudit.get()));
            factory.when(()->SkillFactory.getSkill(1321000)).thenReturn(skill);
            factory.when(()->SkillFactory.getSkill(1121008)).thenReturn(skill);
            factory.when(()->SkillFactory.getSkillName(1321000)).thenReturn("Known skill");
            var edit=request(service,1321000,"set",20,30,false);
            var preview=service.edit(edit,"probe",false);
            assertEquals(1,count("SELECT skilllevel FROM skills WHERE characterid=123 AND skillid=1321000"));
            assertEquals(0,count("SELECT COUNT(*) FROM admin_skill_edits"));
            assertThrows(BizException.class,()->service.edit(edit,"probe",true));
            failAudit.set(true);
            assertThrows(SQLException.class,()->service.edit(confirm(edit,preview),"probe",true));
            assertEquals(1,count("SELECT skilllevel FROM skills WHERE characterid=123 AND skillid=1321000"));
            failAudit.set(false);
            service.edit(confirm(edit,preview),"probe",true);
            assertEquals(20,count("SELECT skilllevel FROM skills WHERE characterid=123 AND skillid=1321000"));
            assertEquals(123456789,count("SELECT expiration FROM skills WHERE characterid=123 AND skillid=1321000"));
            assertEquals(1,count("SELECT COUNT(*) FROM admin_skill_edits"));
            assertThrows(BizException.class,()->service.edit(confirm(edit,preview),"probe",true));
            var deniedCross=request(service,1121008,"set",30,30,false);
            assertThrows(BizException.class,()->service.edit(deniedCross,"probe",false));
            var cross=request(service,1121008,"set",30,30,true);
            var crossPreview=service.edit(cross,"probe",false);service.edit(confirm(cross,crossPreview),"probe",true);
            assertEquals(-1,count("SELECT expiration FROM skills WHERE characterid=123 AND skillid=1121008"));
            var blocked=request(service,1321000,"set",21,30,false);
            sql("UPDATE accounts SET loggedin=2 WHERE id=456");
            assertThrows(BizException.class,()->service.edit(blocked,"probe",false));
            sql("UPDATE accounts SET loggedin=0 WHERE id=456");
            var remove=request(service,1321000,"remove",0,0,false);
            var removePreview=service.edit(remove,"probe",false);service.edit(confirm(remove,removePreview),"probe",true);
            assertEquals(0,count("SELECT COUNT(*) FROM skills WHERE characterid=123 AND skillid=1321000"));
            assertEquals(3,service.history(123).size());
            System.out.println("PASS isolated JDBC: preview read-only, confirmation required, audit rollback, atomic save, expiration preservation, stale rejection, cross-career opt-in, new permanent grant, account lock guard, removal and history");
        }
    }
}
