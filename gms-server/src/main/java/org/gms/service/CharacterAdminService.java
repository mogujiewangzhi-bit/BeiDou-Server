package org.gms.service;

import com.alibaba.fastjson2.JSON;
import org.gms.client.Character;
import org.gms.client.Job;
import org.gms.config.GameConfig;
import org.gms.constants.game.ExpTable;
import org.gms.exception.BizException;
import org.gms.net.server.Server;
import org.gms.provider.Data;
import org.gms.provider.DataProviderFactory;
import org.gms.provider.wz.WZFiles;
import org.gms.server.ItemInformationProvider;
import org.gms.util.DatabaseConnection;
import org.gms.util.I18nUtil;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CharacterAdminService {
    public record Field(String key, String group, int min, int max, String type) {}
    public record Edit(int cid, String revision, Map<String,Object> changes, String previewHash) {}
    private static Field field(String key,String group,int min,int max){return new Field(key,group,min,max,"number");}
    public static final List<Field> FIELDS = List.of(
        field("level","basic",1,200),field("job","basic",0,2218),field("exp","basic",0,Integer.MAX_VALUE),
        field("str","basic",4,32767),field("dex","basic",4,32767),field("int","basic",4,32767),field("luk","basic",4,32767),
        field("hp","basic",0,30000),field("maxhp","basic",1,30000),field("mp","basic",0,30000),field("maxmp","basic",0,30000),
        field("ap","basic",0,32767),new Field("sp","basic",0,32767,"sp"),field("hpMpUsed","basic",0,32767),
        field("meso","basic",0,Integer.MAX_VALUE),field("fame","basic",-32768,32767),field("gm","basic",0,6),
        field("gender","appearance",0,1),field("skincolor","appearance",0,13),field("face","appearance",20000,29999),field("hair","appearance",30000,49999),
        field("map","location",0,999999999),field("spawnpoint","location",0,127),
        field("equipslots","inventory",1,96),field("useslots","inventory",1,96),field("setupslots","inventory",1,96),field("etcslots","inventory",1,96),
        field("buddyCapacity","inventory",1,100),
        field("mountlevel","progress",1,30),field("mountexp","progress",0,Integer.MAX_VALUE),field("mounttiredness","progress",0,100),
        field("reborns","progress",0,Integer.MAX_VALUE),field("pqpoints","progress",0,Integer.MAX_VALUE),
        field("dojoPoints","progress",0,32767),field("ariantPoints","progress",0,32767),
        field("vanquisherStage","progress",0,Integer.MAX_VALUE),field("vanquisherKills","progress",0,Integer.MAX_VALUE),
        field("gachaexp","progress",0,Integer.MAX_VALUE),
        field("nxCredit","account",0,Integer.MAX_VALUE),field("maplePoint","account",0,Integer.MAX_VALUE),field("nxPrepaid","account",0,Integer.MAX_VALUE));
    private static final List<String> ACCOUNT_FIELDS = List.of("nxCredit","maplePoint","nxPrepaid");
    private static final List<String> READ_ONLY_FIELDS = List.of("id","accountid","world","name","guildid","guildrank","party","familyId","partnerId","createdate","lastLogoutTime");

    static String canonicalField(String column) {
        for(Field field:FIELDS)if(field.key().equalsIgnoreCase(column))return field.key();
        for(String key:READ_ONLY_FIELDS)if(key.equalsIgnoreCase(column))return key;
        return column;
    }

    static BizException failure(String reason){return new BizException(I18nUtil.getExceptionMessage("CharacterAdmin."+reason));}
    static String hash(Object value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public Map<String,Object> options(String keyword,Integer world,Integer id,int page) throws SQLException {
        if(page<1 || page>10000 || (world!=null && world<0) || (id!=null && id<1) || keyword==null || keyword.length()>64)throw failure("invalid");
        String pattern="%"+keyword.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        List<Map<String,Object>> rows=new ArrayList<>();
        String sql="SELECT c.id,c.name,c.world,c.job,c.level,a.name AS account FROM characters c JOIN accounts a ON a.id=c.accountid WHERE (c.name LIKE ? ESCAPE '!' OR CAST(c.id AS CHAR) LIKE ? ESCAPE '!')"
            +(world==null?"":" AND c.world=?")+(id==null?"":" AND c.id=?")+" ORDER BY c.id LIMIT 51 OFFSET ?";
        try(Connection con=DatabaseConnection.getConnection();PreparedStatement ps=con.prepareStatement(sql)) {
            int index=1;ps.setString(index++,pattern);ps.setString(index++,pattern);
            if(world!=null)ps.setInt(index++,world);if(id!=null)ps.setInt(index++,id);ps.setInt(index,(page-1)*50);
            try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                Map<String,Object> row=new LinkedHashMap<>(); int cid=rs.getInt("id"),wid=rs.getInt("world");
                row.put("id",cid);row.put("name",rs.getString("name"));row.put("world",wid);row.put("account",rs.getString("account"));
                row.put("job",rs.getInt("job"));row.put("level",rs.getInt("level"));rows.add(row);
            }}
        }
        for(var row:rows) {
            Character online=online(number(row,"id"),number(row,"world"));
            int job=online==null?number(row,"job"):online.getJob().getId();
            row.put("job",job);row.put("jobName",Job.getById(job).getName());row.put("online",online!=null);
            if(online!=null)row.put("level",online.getLevel());
        }
        boolean more=rows.size()>50;if(more)rows.removeLast();
        return Map.of("records",rows,"hasMore",more);
    }
    private Character online(int cid,int world){var w=Server.getInstance().getWorld(world);return w==null?null:w.getPlayerStorage().getCharacterById(cid);}

    private Map<String,Object> snapshot(Connection con,int cid,boolean lock) throws SQLException {
        Map<String,Object> row=new LinkedHashMap<>();
        try(PreparedStatement ps=con.prepareStatement("SELECT * FROM characters WHERE id=?"+(lock?" FOR UPDATE":""))) {
            ps.setInt(1,cid);try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())throw failure("missing");
                for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                    String key=canonicalField(rs.getMetaData().getColumnLabel(i));Object value=rs.getObject(i);
                    if(value instanceof Boolean flag && FIELDS.stream().anyMatch(f->f.key().equals(key) && f.type().equals("number")))value=flag?1:0;
                    row.put(key,value);
                }
            }
        }
        try(PreparedStatement ps=con.prepareStatement("SELECT name,nxCredit,maplePoint,nxPrepaid,loggedin FROM accounts WHERE id=?")) {
            ps.setInt(1,((Number)row.get("accountid")).intValue());
            try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())throw failure("missing");
                row.put("accountName",rs.getString("name"));row.put("accountLoggedIn",rs.getInt("loggedin"));
                for(String key:ACCOUNT_FIELDS)row.put(key,rs.getInt(key));
            }
        }
        return row;
    }
    public Map<String,Object> detail(int cid) throws SQLException {
        Map<String,Object> row;
        try(Connection con=DatabaseConnection.getConnection()) {
            row=snapshot(con,cid,false);
        }
        boolean online=online(cid,((Number)row.get("world")).intValue())!=null;
        boolean editable=!online && ((Number)row.get("accountLoggedIn")).intValue()==0;
        return Map.of("values",row,"revision",hash(row),"editable",editable,"online",online,"fields",FIELDS,
            "jobs",Arrays.stream(Job.values()).map(j->Map.of("value",j.getId(),"label",j.getName())).toList());
    }
    static Map<String,Object> patch(Map<String,Object> before,Map<String,Object> changes) {
        if(changes==null || changes.isEmpty() || changes.size()>FIELDS.size())throw failure("invalid");
        Map<String,Object> after=new LinkedHashMap<>(before);
        for(var entry:changes.entrySet()) {
            Field field=FIELDS.stream().filter(f->f.key().equals(entry.getKey())).findFirst().orElseThrow(()->failure("invalid"));
            Object value=entry.getValue();
            if(field.type().equals("sp")) {
                if(!(value instanceof String text) || !text.matches("[0-9]{1,5}(,[0-9]{1,5}){9}"))throw failure("invalid");
                if(Arrays.stream(text.split(",")).anyMatch(s->Integer.parseInt(s)>32767))throw failure("invalid");
                value=String.join(",",Arrays.stream(text.split(",")).map(s->Integer.toString(Integer.parseInt(s))).toList());
            } else {
                if(!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=n.longValue() || n.longValue()<field.min() || n.longValue()>field.max())throw failure("invalid");
                value=((Number)value).intValue();
            }
            after.put(field.key(),value);
        }
        Job job=Job.getById(number(after,"job"));
        if(Arrays.stream(Job.values()).noneMatch(j->j.getId()==number(after,"job")) || number(after,"level")>Character.getMaxClassLevel(job)
            || number(after,"hp")>number(after,"maxhp") || number(after,"mp")>number(after,"maxmp"))throw failure("invalid");
        int level=number(after,"level"),exp=number(after,"exp");
        if((level==Character.getMaxClassLevel(job) && exp!=0) || (level<Character.getMaxClassLevel(job) && exp>=ExpTable.getExpNeededForLevel(level)))throw failure("exp");
        for(String key:List.of("str","dex","int","luk"))if(number(after,key)>GameConfig.getServerInt("max_ap"))throw failure("invalid");
        return after;
    }
    private static int number(Map<String,Object> row,String key){return ((Number)row.get(key)).intValue();}

    private void resources(Connection con,int cid,Map<String,Object> before,Map<String,Object> after,Map<String,Object> changes) throws SQLException {
        for(String key:List.of("face","hair"))if(changes.containsKey(key) && !Objects.equals(before.get(key),after.get(key))) {
            String path=(key.equals("face")?"Face/":"Hair/")+String.format("%08d.img",number(after,key));
            if(DataProviderFactory.getDataProvider(WZFiles.CHARACTER).getData(path)==null)throw failure("resource");
        }
        if(changes.containsKey("skincolor") && !Objects.equals(before.get("skincolor"),after.get("skincolor"))) {
            if(DataProviderFactory.getDataProvider(WZFiles.CHARACTER).getData(String.format("%08d.img",2000+number(after,"skincolor")))==null)throw failure("resource");
        }
        if(changes.containsKey("map") || changes.containsKey("spawnpoint")) {
            int map=number(after,"map");Data data=DataProviderFactory.getDataProvider(WZFiles.MAP).getData("Map/Map"+(map/100000000)+"/"+String.format("%09d.img",map));
            if(data==null || data.getChildByPath("portal/"+number(after,"spawnpoint"))==null)throw failure("resource");
        }
        var ii=ItemInformationProvider.getInstance();
        int niche=Job.getById(number(after,"job")).getJobNiche(),mask=niche>=1&&niche<=5?1<<(niche-1):0;
        try(PreparedStatement ps=con.prepareStatement("SELECT itemid,inventorytype,position FROM inventoryitems WHERE characterid=? AND type=1 AND inventorytype BETWEEN -1 AND 4")) {
            ps.setInt(1,cid);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                int type=rs.getInt("inventorytype"),position=rs.getInt("position"),item=rs.getInt("itemid");
                if(type>=1 && position>number(after,List.of("equipslots","useslots","setupslots","etcslots").get(type-1)))throw failure("capacity");
                if(type!=-1 || ii.isCash(item))continue;
                var req=ii.getEquipStats(item);
                if(req==null || req.getOrDefault("reqLevel",0)>number(after,"level") || (mask!=0 && req.getOrDefault("reqJob",0)>0 && (req.get("reqJob")&mask)==0))throw failure("equipment");
                for(String stat:List.of("str","dex","int","luk"))if(req.getOrDefault("req"+stat.toUpperCase(),0)>number(after,stat))throw failure("equipment");
            }}
        }
    }
    public Map<String,Object> edit(Edit request,String actor,boolean save) throws SQLException {
        if(request==null || request.cid()<1 || request.revision()==null || request.revision().length()!=64)throw failure("invalid");
        int world;
        try(Connection con=DatabaseConnection.getConnection();PreparedStatement ps=con.prepareStatement("SELECT world FROM characters WHERE id=?")) {
            ps.setInt(1,request.cid());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw failure("missing");world=rs.getInt(1);}
        }
        // Do not acquire game-world locks while holding database row locks.
        if(online(request.cid(),world)!=null)throw failure("online");
        try(Connection con=DatabaseConnection.getConnection()) {
            con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);con.setAutoCommit(false);
            try {
                int account;
                try(PreparedStatement ps=con.prepareStatement("SELECT accountid FROM characters WHERE id=?")){
                    ps.setInt(1,request.cid());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw failure("missing");account=rs.getInt(1);}
                }
                // Login state changes use this account row too; lock it before the character row.
                try(PreparedStatement ps=con.prepareStatement("SELECT loggedin FROM accounts WHERE id=? FOR UPDATE")) {
                    ps.setInt(1,account);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw failure("missing");if(rs.getInt(1)!=0)throw failure("online");}
                }
                var before=snapshot(con,request.cid(),true);
                if(number(before,"accountid")!=account || number(before,"world")!=world)throw failure("stale");
                if(!hash(before).equals(request.revision()))throw failure("stale");
                var after=patch(before,request.changes());resources(con,request.cid(),before,after,request.changes());
                List<Map<String,Object>> diff=new ArrayList<>();
                // Fixed metadata order keeps confirmation hashes independent of JSON key order.
                for(Field field:FIELDS)if(request.changes().containsKey(field.key()) && !Objects.equals(before.get(field.key()),after.get(field.key()))) {
                    Map<String,Object> item=new LinkedHashMap<>();item.put("key",field.key());item.put("before",before.get(field.key()));item.put("after",after.get(field.key()));diff.add(item);
                }
                if(diff.isEmpty())throw failure("unchanged");
                String previewHash=hash(List.of(request.cid(),request.revision(),diff));
                if(!save){con.rollback();return Map.of("diff",diff,"previewHash",previewHash,"characterName",before.get("name"));}
                if(!previewHash.equals(request.previewHash()))throw failure("preview");
                UUID audit=UUID.randomUUID();
                for(boolean accountFields:new boolean[]{false,true}) {
                    var keys=FIELDS.stream().map(Field::key).filter(k->request.changes().containsKey(k) && ACCOUNT_FIELDS.contains(k)==accountFields).toList();
                    if(keys.isEmpty())continue;
                    String assignments=String.join(",",keys.stream().map(k->"`"+k+"`=?").toList());
                    try(PreparedStatement ps=con.prepareStatement("UPDATE "+(accountFields?"accounts":"characters")+" SET "+assignments+" WHERE id=?")){
                        int i=1;for(String key:keys)ps.setObject(i++,after.get(key));ps.setInt(i,accountFields?account:request.cid());if(ps.executeUpdate()!=1)throw failure("stale");
                    }
                }
                var persisted=snapshot(con,request.cid(),false);
                try(PreparedStatement ps=con.prepareStatement("INSERT INTO admin_character_edits(id,character_id,account_id,actor,before_state,after_state,changes) VALUES(?,?,?,?,?,?,?)")) {
                    ps.setString(1,audit.toString());ps.setInt(2,request.cid());ps.setInt(3,account);ps.setString(4,actor);
                    ps.setString(5,JSON.toJSONString(before));ps.setString(6,JSON.toJSONString(persisted));ps.setString(7,JSON.toJSONString(diff));ps.executeUpdate();
                }
                con.commit();return Map.of("auditId",audit.toString());
            } catch(Exception e){con.rollback();throw e;}
        }
    }
    public List<Map<String,Object>> history(int cid) throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection con=DatabaseConnection.getConnection();PreparedStatement ps=con.prepareStatement("SELECT id,actor,changes,created_at FROM admin_character_edits WHERE character_id=? ORDER BY created_at DESC LIMIT 100")){
            ps.setInt(1,cid);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                Map<String,Object> row=new LinkedHashMap<>();row.put("id",rs.getString("id"));row.put("actor",rs.getString("actor"));row.put("changes",JSON.parseArray(rs.getString("changes")));row.put("createdAt",rs.getTimestamp("created_at"));rows.add(row);
            }}
        }return rows;
    }
}
