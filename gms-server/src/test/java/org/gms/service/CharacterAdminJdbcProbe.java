package org.gms.service;

import org.gms.config.GameConfig;
import org.gms.exception.BizException;
import org.gms.net.server.Server;
import org.gms.server.ItemInformationProvider;
import org.gms.provider.DataProviderFactory;
import org.gms.provider.DataProvider;
import org.gms.util.DatabaseConnection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/** Only runs inside the isolated verification schema, never against user characters. */
public class CharacterAdminJdbcProbe {
    private static Connection open() throws SQLException {
        String url=System.getenv("SUPERCHAR_JDBC");
        if(url==null || !url.contains("/codex_character_admin_check?"))throw new IllegalArgumentException("isolated-schema-required");
        return DriverManager.getConnection(url,System.getenv("SUPERCHAR_USER"),System.getenv("SUPERCHAR_PASSWORD"));
    }
    private static void sql(String sql) throws SQLException {try(Connection con=open();var ps=con.prepareStatement(sql)){ps.executeUpdate();}}
    private static Connection fault(String point) throws SQLException {
        Connection real=open();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement") && args[0] instanceof String query && point!=null) {
                if((point.equals("audit") && query.startsWith("INSERT INTO admin_character_edits"))
                    || (point.equals("account") && query.startsWith("UPDATE accounts")))throw new SQLException("injected-"+point);
            }
            try {return method.invoke(real,args);} catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    private static CharacterAdminService.Edit request(CharacterAdminService service,Map<String,Object> changes) throws SQLException {
        return new CharacterAdminService.Edit(123,(String)service.detail(123).get("revision"),changes,null);
    }
    private static void pass(String text){System.out.println("PASS "+text);}
    public static void main(String[] args) throws Exception {
        OfflineGrantServiceTest.bootstrapMessages();
        ItemInformationProvider.getInstance();
        Server.getInstance();
        sql("INSERT INTO accounts (id,name,password,loggedin,banned) VALUES (456,'CMProbe','disabled-test',0,1)");
        sql("INSERT INTO characters (id,accountid,name,gm,job,level,exp,str,dex,`int`,luk,hp,mp,maxhp,maxmp,sp,equipslots,useslots,setupslots,etcslots) VALUES (123,456,'CM%_Probe',6,132,200,0,12,5,4,4,100,50,100,50,'0,0,0,0,0,0,0,0,0,0',24,24,24,24)");
        sql("INSERT INTO characters (id,accountid,name,gm,job,level) VALUES (124,456,'CMOther',0,0,1)");
        sql("INSERT INTO inventoryitems (type,characterid,itemid,inventorytype,position,quantity,owner,flag,giftFrom) VALUES(1,123,2000005,2,24,1,'',0,'')");
        sql("INSERT INTO inventoryitems (type,characterid,itemid,inventorytype,position,quantity,owner,flag,giftFrom) VALUES(2,123,2000005,2,90,1,'',0,'')");
        AtomicReference<String> failure=new AtomicReference<>();
        try(var database=mockStatic(DatabaseConnection.class);var config=mockStatic(GameConfig.class);
            var server=mockStatic(Server.class);var items=mockStatic(ItemInformationProvider.class)) {
            database.when(DatabaseConnection::getConnection).thenAnswer(call->fault(failure.get()));
            config.when(()->GameConfig.getServerInt("max_ap")).thenReturn(32767);
            server.when(Server::getInstance).thenReturn(mock(Server.class));
            var provider=mock(ItemInformationProvider.class);
            items.when(ItemInformationProvider::getInstance).thenReturn(provider);
            var service=new CharacterAdminService();
            var records=(List<?>)service.options("%_",null,null,1).get("records");assertEquals(1,records.size());
            assertEquals(1,((List<?>)service.options("123",null,null,1).get("records")).size());pass("fuzzy ID and literal wildcard name search");
            for(int i=0;i<52;i++)sql("INSERT INTO characters(id,accountid,name,job,level) VALUES("+(1000+i)+",456,'CMBulk"+i+"',0,1)");
            assertTrue((boolean)service.options("",null,null,1).get("hasMore"));assertEquals(4,((List<?>)service.options("",null,null,2).get("records")).size());pass("dropdown pagination and load more");
            var before=service.detail(123);var first=request(service,Map.of("str",999,"nxCredit",10000));
            var preview=service.edit(first,"test-admin",false);assertEquals(before.get("revision"),service.detail(123).get("revision"));assertTrue(service.history(123).isEmpty());pass("preview has no side effects");
            assertThrows(BizException.class,()->service.edit(first,"test-admin",true));pass("save without matching preview rejected");
            var confirmed=new CharacterAdminService.Edit(first.cid(),first.revision(),first.changes(),(String)preview.get("previewHash"));
            failure.set("audit");assertThrows(SQLException.class,()->service.edit(confirmed,"test-admin",true));failure.set(null);
            assertEquals(before.get("revision"),service.detail(123).get("revision"));assertTrue(service.history(123).isEmpty());pass("audit failure rolls back");
            failure.set("account");assertThrows(SQLException.class,()->service.edit(confirmed,"test-admin",true));failure.set(null);
            assertEquals(before.get("revision"),service.detail(123).get("revision"));assertTrue(service.history(123).isEmpty());pass("account update failure rolls back character and audit");
            service.edit(confirmed,"test-admin",true);var after=(Map<?,?>)service.detail(123).get("values");assertEquals(999,after.get("str"));assertEquals(10000,after.get("nxCredit"));assertEquals(1,service.history(123).size());pass("character and shared account balances saved atomically with audit");
            assertThrows(BizException.class,()->service.edit(confirmed,"test-admin",true));pass("repeat or stale confirmation rejected");
            assertThrows(BizException.class,()->service.edit(request(service,Map.of("useslots",10)),"test-admin",false));pass("inventory shrinking cannot orphan items");
            sql("INSERT INTO inventoryitems(type,characterid,itemid,inventorytype,position,quantity,owner,flag,giftFrom) VALUES(1,123,1000000,-1,-1,1,'',0,'')");
            when(provider.getEquipStats(anyInt())).thenReturn(Map.of("reqJob",1,"reqSTR",9999));
            assertThrows(BizException.class,()->service.edit(request(service,Map.of("str",1000)),"test-admin",false));
            sql("DELETE FROM inventoryitems WHERE characterid=123 AND inventorytype=-1");pass("incompatible worn equipment rejected");
            try(var wz=mockStatic(DataProviderFactory.class)) {
                wz.when(()->DataProviderFactory.getDataProvider(any())).thenReturn(mock(DataProvider.class));
                assertThrows(BizException.class,()->service.edit(request(service,Map.of("face",29999)),"test-admin",false));
            }pass("missing appearance WZ rejected");
            sql("UPDATE accounts SET loggedin=2 WHERE id=456");
            assertFalse((boolean)service.detail(123).get("editable"));assertThrows(BizException.class,()->service.edit(request(service,Map.of("str",1000)),"test-admin",false));
            sql("UPDATE accounts SET loggedin=0 WHERE id=456");pass("online account cannot be edited");
            var grow=request(service,Map.of("useslots",96,"hp",30000,"maxhp",30000,"mp",30000,"maxmp",30000));
            var growPreview=service.edit(grow,"test-admin",false);service.edit(new CharacterAdminService.Edit(grow.cid(),grow.revision(),grow.changes(),(String)growPreview.get("previewHash")),"test-admin",true);
            var grown=(Map<?,?>)service.detail(123).get("values");assertEquals(96,grown.get("useslots"));assertEquals(30000,grown.get("hp"));pass("HP MP and inventory custom values persisted");
            assertThrows(BizException.class,()->service.edit(request(service,Map.of("accountid",999)),"test-admin",false));pass("identity fields cannot be changed");
        }
        System.out.println("ALL CHARACTER ADMIN ISOLATED CHECKS PASSED");
    }
}
