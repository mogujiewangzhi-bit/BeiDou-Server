package org.gms.server.superchar;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.gms.client.Character;
import org.gms.client.Skill;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.ItemFactory;
import org.gms.constants.net.ServerConstants;
import org.gms.model.pojo.SkillEntry;
import org.gms.util.DatabaseConnection;
import org.gms.util.I18nUtil;
import org.gms.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Narrow JDBC transaction: no independent characterService or asynchronous autosave calls. */
public final class SuperCharacterStore {
    private static final Logger log=LoggerFactory.getLogger(SuperCharacterStore.class);
    public record Result(String backupId,boolean reload) {}
    @FunctionalInterface public interface ConnectionSource {Connection open() throws SQLException;}
    private final ConnectionSource source;
    private final Path backupDirectory;
    public SuperCharacterStore(ConnectionSource source,Path backupDirectory) {this.source=source;this.backupDirectory=backupDirectory;}
    public SuperCharacterStore() {this(DatabaseConnection::getConnection,Path.of("backups","superchar"));}
    public Result execute(Character chr,SuperCharacterPlan plan) {
        if (!Thread.holdsLock(chr)) throw new IllegalArgumentException("busy");
        SuperCharacterPlan.checkState(chr);
        String id=UUID.randomUUID().toString();
        boolean commitStarted=false,committed=false;
        try(Connection con=source.open()) {
            con.setAutoCommit(false);
            try {
                try(PreparedStatement ps=con.prepareStatement("SELECT accountid, gm FROM characters WHERE id=? FOR UPDATE")) {
                    ps.setInt(1,chr.getId());
                    try(ResultSet rs=ps.executeQuery()) {
                        if(!rs.next() || rs.getInt(1)!=chr.getAccountId() || rs.getInt(2)!=6)throw new IllegalArgumentException("permission");
                    }
                }
                // Materialize the actual live pre-operation state inside this same uncommitted transaction.
                // A failed operation rolls this back too; preview never reaches this method.
                Map<String,Integer> original=new LinkedHashMap<>();
                original.put("str",chr.getStr());original.put("dex",chr.getDex());original.put("int",chr.getInt());original.put("luk",chr.getLuk());
                writeCharacter(con,chr.getId(),chr.getJob().getId(),chr.getLevel(),chr.getExp(),original,
                    chr.getHp(),chr.getMp(),chr.getMaxHp(),chr.getMaxMp(),chr.getRemainingAp(),chr.getRemainingSps(),
                    new int[]{chr.getSlots(1),chr.getSlots(2),chr.getSlots(3),chr.getSlots(4)});
                writeSkills(con,chr.getId(),chr.getSkills());
                List<Pair<Item,InventoryType>> items=plan.inventory(chr);
                List<Pair<Item,InventoryType>> beforeItems=plan.originalInventory(chr);
                ItemFactory.INVENTORY.saveItems(beforeItems,chr.getId(),con);
                JSONObject before=snapshot(con,chr.getId());
                JSONObject backup=new JSONObject();
                backup.put("id",id);backup.put("characterId",chr.getId());backup.put("accountId",chr.getAccountId());
                backup.put("createdAt",Instant.now().toString());backup.put("serverVersion",ServerConstants.BEI_DOU_VERSION);
                backup.put("before",before);backup.put("cashSlots",chr.getSlots(5));
                JSONObject parameters=(JSONObject)JSON.toJSON(plan.options());
                parameters.put("careerSkillsSkipped",plan.careerSkillsSkipped());
                parameters.put("suppliesGranted",Map.of("2000005",plan.supplies().superPotionAdded(),"2050004",plan.supplies().allCureAdded()));
                backup.put("parameters",parameters);
                writeBackup(id,backup);
                int maximum=Character.getMaxInventorySlots();
                writeCharacter(con,chr.getId(),plan.options().career().job(),plan.level(),0,plan.attributes(),30000,30000,30000,30000,0,new int[10],new int[]{maximum,maximum,maximum,maximum});
                writeSkills(con,chr.getId(),plan.skills());
                ItemFactory.INVENTORY.saveItems(items,chr.getId(),con);
                JSONObject after=snapshot(con,chr.getId());
                try(PreparedStatement ps=con.prepareStatement("INSERT INTO superchar_audit (id,characterid,accountid,parameters,before_state,after_state) VALUES (?,?,?,?,?,?)")) {
                    ps.setString(1,id);ps.setInt(2,chr.getId());ps.setInt(3,chr.getAccountId());
                    ps.setString(4,JSON.toJSONString(parameters));ps.setString(5,JSON.toJSONString(before));ps.setString(6,JSON.toJSONString(after));ps.executeUpdate();
                }
                commitStarted=true;
                con.commit();
                committed=true;
            } catch(Exception e) {
                try {con.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
                if(commitStarted) {
                    try(Connection check=source.open();PreparedStatement ps=check.prepareStatement("SELECT id FROM superchar_audit WHERE id=?")) {
                        ps.setString(1,id);try(ResultSet rs=ps.executeQuery()){committed=rs.next();}
                    }catch(Exception unknown){chr.requireSuperCharacterReload();throw new IllegalStateException("commit-unknown",unknown);}
                }
                if(!committed)throw e;
            }
        } catch(Exception e) {
            if(!committed)throw new IllegalStateException("superchar-failed",e);
            // Closing a connection after a successful commit does not mean the operation failed.
        }
        boolean reload=false;
        try {chr.applyCommittedSuperCharacter(plan);}catch(Exception e) {
            reload=true;chr.requireSuperCharacterReload();
            log.warn(I18nUtil.getLogMessage("SuperCharacter.syncFailed"),id,chr.getId());
        }
        log.info(I18nUtil.getLogMessage("SuperCharacter.audit"),id,chr.getId(),chr.getName(),plan.options().key(),plan.level(),plan.changed(),plan.removed(),plan.added().size(),plan.skipped());
        return new Result(id,reload);
    }
    private void writeBackup(String id,JSONObject backup) throws Exception {
        writeBackup(backupDirectory, id, backup);
    }
    static void writeBackup(Path backupDirectory,String id,JSONObject backup) throws Exception {
        Files.createDirectories(backupDirectory);
        if(Files.getFileStore(backupDirectory).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(backupDirectory,PosixFilePermissions.fromString("rwx------"));
        Path file=backupDirectory.resolve(id+".json");
        try(var channel=FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            if(Files.getFileStore(file).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
            byte[] bytes=JSON.toJSONString(backup).getBytes(StandardCharsets.UTF_8);
            var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);
            channel.force(true);
        }
    }
    static void writeCharacter(Connection con,int id,int job,int level,int exp,Map<String,Integer> attributes,int hp,int mp,int maxHp,int maxMp,int ap,int[] sp,int[] slots) throws SQLException {
        try(PreparedStatement ps=con.prepareStatement("UPDATE characters SET job=?,level=?,exp=?,str=?,dex=?,`int`=?,luk=?,hp=?,mp=?,maxhp=?,maxmp=?,ap=?,sp=?,equipslots=?,useslots=?,setupslots=?,etcslots=? WHERE id=?")) {
            int n=1;ps.setInt(n++,job);ps.setInt(n++,level);ps.setInt(n++,exp);
            for(String key:List.of("str","dex","int","luk"))ps.setInt(n++,attributes.get(key));
            for(int value:new int[]{hp,mp,maxHp,maxMp,ap})ps.setInt(n++,value);
            StringBuilder value=new StringBuilder();for(int points:sp){if(!value.isEmpty())value.append(',');value.append(points);}
            ps.setString(n++,value.toString());for(int slot:slots)ps.setInt(n++,slot);ps.setInt(n,id);
            if(ps.executeUpdate()!=1)throw new SQLException("character-row-missing");
        }
    }
    static void writeSkills(Connection con,int id,Map<Skill,SkillEntry> skills) throws SQLException {
        try(PreparedStatement ps=con.prepareStatement("DELETE FROM skills WHERE characterid=?")){ps.setInt(1,id);ps.executeUpdate();}
        try(PreparedStatement ps=con.prepareStatement("INSERT INTO skills (characterid,skillid,skilllevel,masterlevel,expiration) VALUES (?,?,?,?,?)")) {
            for(var entry:skills.entrySet()) {
                ps.setInt(1,id);ps.setInt(2,entry.getKey().getId());ps.setInt(3,entry.getValue().skillLevel);
                ps.setInt(4,entry.getValue().masterLevel);ps.setLong(5,entry.getValue().expiration);ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    public static JSONObject snapshot(Connection con,int id) throws SQLException {
        JSONObject result=new JSONObject();
        result.put("character",rows(con,"SELECT * FROM characters WHERE id=?",id));
        result.put("skills",rows(con,"SELECT skillid,skilllevel,masterlevel,expiration FROM skills WHERE characterid=? ORDER BY skillid",id));
        result.put("items",rows(con,"SELECT * FROM inventoryitems WHERE characterid=? AND type=1 ORDER BY inventoryitemid",id));
        result.put("equipment",rows(con,"SELECT e.* FROM inventoryequipment e JOIN inventoryitems i USING(inventoryitemid) WHERE i.characterid=? AND i.type=1 ORDER BY e.inventoryitemid",id));
        return result;
    }
    private static JSONArray rows(Connection con,String sql,int id) throws SQLException {
        JSONArray list=new JSONArray();
        try(PreparedStatement ps=con.prepareStatement(sql)) {
            ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()) {
                ResultSetMetaData metadata=rs.getMetaData();
                while(rs.next()) {
                    JSONObject row=new JSONObject();for(int col=1;col<=metadata.getColumnCount();col++)row.put(metadata.getColumnLabel(col),rs.getObject(col));list.add(row);
                }
            }
        }
        return list;
    }
}
