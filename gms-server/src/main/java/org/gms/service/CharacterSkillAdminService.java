package org.gms.service;

import com.alibaba.fastjson2.JSON;
import org.gms.client.Job;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.exception.BizException;
import org.gms.provider.DataProviderFactory;
import org.gms.provider.DataTool;
import org.gms.provider.wz.WZFiles;
import org.gms.util.DatabaseConnection;
import org.gms.util.I18nUtil;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class CharacterSkillAdminService {
    public record Edit(int cid, String revision, Number skillId, String action, Number level,
                       Number masterLevel, boolean allowCross, String previewHash,
                       String keyMode, Number keyCode, boolean replaceKey) {
        public Edit(int cid,String revision,Number skillId,String action,Number level,Number masterLevel,boolean allowCross,String previewHash) {
            this(cid,revision,skillId,action,level,masterLevel,allowCross,previewHash,null,null,false);
        }
    }
    private final CharacterAdminService characters;
    private volatile List<Integer> catalogIds;
    private volatile Map<Integer,String> skillNames;
    public CharacterSkillAdminService(CharacterAdminService characters) { this.characters=characters; }
    static BizException failure(String reason) { return new BizException(I18nUtil.getExceptionMessage("SkillAdmin."+reason)); }
    static int integer(Number value,int min,int max) {
        if(value==null || !Double.isFinite(value.doubleValue()) || value.doubleValue()!=value.longValue()
            || value.longValue()<min || value.longValue()>max)throw failure("invalid");
        return value.intValue();
    }
    static boolean compatible(int id,int job) {
        int book=id/10000;
        if(book==job)return true;
        if(job>=2200 && job<=2218)return book==2001 || (book>=2200 && book<=job);
        if(book==job/1000*1000)return true;
        return book==job/100*100 || (book/10==job/10 && book<=job);
    }
    static boolean gmSkill(int id) { return id/10000==900 || id/10000==910; }
    static Map<String,Object> validate(Edit edit,int job,int gm,Skill skill,Map<String,Object> existing) {
        if(edit==null || !(Objects.equals(edit.action(),"set") || Objects.equals(edit.action(),"remove")))throw failure("invalid");
        int id=integer(edit.skillId(),1,Integer.MAX_VALUE);
        if(edit.action().equals("remove")) { if(existing==null)throw failure("unchanged"); return null; }
        if(skill==null || skill.getMaxLevel()<1 || skill.getMaxLevel()>127)throw failure("missingSkill");
        if(gmSkill(id) && (gm<1 || (id/10000==910 && gm<6)))throw failure("gm");
        if(!compatible(id,job) && !edit.allowCross())throw failure("cross");
        int level=integer(edit.level(),1,skill.getMaxLevel());
        int master=integer(edit.masterLevel(),0,skill.getMaxLevel());
        if((skill.isFourthJob() && (master<level || master<1)) || (!skill.isFourthJob() && master!=0))throw failure("master");
        Map<String,Object> after=new LinkedHashMap<>();
        after.put("id",id);after.put("level",level);after.put("masterLevel",master);
        after.put("expiration",existing==null?-1L:((Number)existing.get("expiration")).longValue());
        return after;
    }
    private List<Integer> ids() {
        if(catalogIds!=null)return catalogIds;
        synchronized(this) {
            if(catalogIds!=null)return catalogIds;
            var provider=DataProviderFactory.getDataProvider(WZFiles.SKILL);
            List<Integer> result=new ArrayList<>();
            for(var file:provider.getRoot().getFiles()) {
                if(!file.getName().matches("[0-9]+\\.img"))continue;
                var data=provider.getData(file.getName());
                var skills=data==null?null:data.getChildByPath("skill");
                if(skills==null)continue;
                for(var entry:skills) {
                    if(!entry.getName().matches("[0-9]+"))continue;
                    int id=Integer.parseInt(entry.getName());Skill skill=SkillFactory.getSkill(id);
                    if(skill!=null && skill.getMaxLevel()>0 && skill.getMaxLevel()<=127)result.add(id);
                }
            }
            var sorted=result.stream().distinct().sorted().toList();
            if(!sorted.isEmpty())catalogIds=sorted;
            return sorted;
        }
    }
    private Map<Integer,String> names() {
        if(skillNames!=null)return skillNames;
        synchronized(this) {
            if(skillNames!=null)return skillNames;
            var data=DataProviderFactory.getDataProvider(WZFiles.STRING).getData("Skill.img");
            Map<Integer,String> result=new LinkedHashMap<>();
            if(data!=null)for(var entry:data) {
                if(!entry.getName().matches("[0-9]{1,10}"))continue;
                int id;
                try{id=Integer.parseInt(entry.getName());}catch(NumberFormatException ignored){continue;}
                String name=DataTool.getString("name",entry,null);
                if(name!=null && !name.isBlank())result.put(id,name);
            }
            skillNames=Map.copyOf(result);
            return skillNames;
        }
    }
    private Map<String,Object> metadata(int id,int job) {
        Skill skill=SkillFactory.getSkill(id);String name=names().get(id);
        int book=id/10000;
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("name",name==null?String.valueOf(id):name);
        row.put("job",book);row.put("jobName",Job.getById(book).getName());row.put("compatible",compatible(id,job));
        row.put("maxLevel",skill==null?0:skill.getMaxLevel());row.put("masterRequired",skill!=null&&skill.isFourthJob());
        row.put("known",skill!=null);row.put("gmSkill",gmSkill(id));row.put("bindable",skill!=null&&SkillShortcutPolicy.bindable(id));return row;
    }
    public Map<String,Object> catalog(int cid,String q,int page) throws SQLException {
        if(q==null || q.length()>64 || page<1 || page>10000)throw failure("invalid");
        var detail=characters.detail(cid);
        @SuppressWarnings("unchecked") var values=(Map<String,Object>)detail.get("values");
        int job=((Number)values.get("job")).intValue();String keyword=q.strip().toLowerCase(Locale.ROOT);
        var matches=ids().stream().map(id->metadata(id,job)).filter(row->String.valueOf(row.get("id")).contains(keyword)
            || String.valueOf(row.get("name")).toLowerCase(Locale.ROOT).contains(keyword)).skip((long)(page-1)*50).limit(51).toList();
        return Map.of("records",matches.subList(0,Math.min(50,matches.size())),"hasMore",matches.size()>50);
    }
    private Map<String,Object> state(Connection con,int cid,boolean lock) throws SQLException {
        Map<String,Object> state=new LinkedHashMap<>();
        try(PreparedStatement ps=con.prepareStatement("SELECT id,accountid,world,name,job,gm FROM characters WHERE id=?"+(lock?" FOR UPDATE":""))) {
            ps.setInt(1,cid);try(ResultSet rs=ps.executeQuery()) { if(!rs.next())throw CharacterAdminService.failure("missing");
                for(String key:List.of("id","accountid","world","name","job","gm"))state.put(key,key.equals("name")?rs.getString(key):rs.getInt(key)); }
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        try(PreparedStatement ps=con.prepareStatement("SELECT skillid,skilllevel,masterlevel,expiration FROM skills WHERE characterid=? ORDER BY skillid"+(lock?" FOR UPDATE":""))) {
            ps.setInt(1,cid);try(ResultSet rs=ps.executeQuery()){while(rs.next()) {
                Map<String,Object> row=new LinkedHashMap<>();row.put("id",rs.getInt(1));row.put("level",rs.getInt(2));row.put("masterLevel",rs.getInt(3));row.put("expiration",rs.getLong(4));rows.add(row);
            }}
        }
        state.put("skills",rows);
        List<Map<String,Object>> bindings=new ArrayList<>();
        try(PreparedStatement ps=con.prepareStatement("SELECT `key`,`type`,`action` FROM keymap WHERE characterid=? ORDER BY `key`"+(lock?" FOR UPDATE":""))) {
            ps.setInt(1,cid);try(ResultSet rs=ps.executeQuery()){while(rs.next())bindings.add(Map.of("key",rs.getInt(1),"type",rs.getInt(2),"action",rs.getInt(3)));}
        }
        state.put("keybindings",bindings);return state;
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> state) { return (List<Map<String,Object>>)state.get("skills"); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> bindings(Map<String,Object> state) { return (List<Map<String,Object>>)state.get("keybindings"); }
    public Map<String,Object> list(int cid) throws SQLException {
        var detail=characters.detail(cid);Map<String,Object> state;
        try(Connection con=DatabaseConnection.getConnection()){state=state(con,cid,false);}
        int job=((Number)state.get("job")).intValue();
        var decorated=rows(state).stream().map(row->{var result=metadata(((Number)row.get("id")).intValue(),job);result.putAll(row);return result;}).toList();
        return Map.of("records",decorated,"revision",CharacterAdminService.hash(state),"editable",detail.get("editable"),"keyOptions",SkillShortcutPolicy.options(bindings(state)));
    }
    public Map<String,Object> edit(Edit request,String actor,boolean save) throws SQLException {
        if(request==null || request.cid()<1 || request.revision()==null || request.revision().length()!=64 || actor==null || actor.isBlank())throw failure("invalid");
        int id=integer(request.skillId(),1,Integer.MAX_VALUE);
        var detail=characters.detail(request.cid());
        if(!Boolean.TRUE.equals(detail.get("editable")))throw CharacterAdminService.failure("online");
        @SuppressWarnings("unchecked") var identity=(Map<String,Object>)detail.get("values");
        int account=((Number)identity.get("accountid")).intValue();
        // Resolve WZ/game information before taking database row locks.
        Skill skill=SkillFactory.getSkill(id);
        try(Connection con=DatabaseConnection.getConnection()) {
            con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);con.setAutoCommit(false);
            try {
                try(PreparedStatement ps=con.prepareStatement("SELECT loggedin FROM accounts WHERE id=? FOR UPDATE")) {
                    ps.setInt(1,account);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw CharacterAdminService.failure("missing");if(rs.getInt(1)!=0)throw CharacterAdminService.failure("online");}
                }
                var before=state(con,request.cid(),true);
                if(((Number)before.get("accountid")).intValue()!=account || !CharacterAdminService.hash(before).equals(request.revision()))throw CharacterAdminService.failure("stale");
                var existing=rows(before).stream().filter(row->((Number)row.get("id")).intValue()==id).findFirst().orElse(null);
                var value=validate(request,((Number)before.get("job")).intValue(),((Number)before.get("gm")).intValue(),skill,existing);
                var shortcut=SkillShortcutPolicy.plan(id,request.action(),request.keyMode(),request.keyCode(),request.replaceKey(),bindings(before));
                if(Objects.equals(existing,value)&&bindings(before).equals(shortcut.bindings()))throw CharacterAdminService.failure("unchanged");
                Map<String,Object> diff=new LinkedHashMap<>();diff.put("skillId",id);diff.put("action",request.action());diff.put("before",existing);diff.put("after",value);diff.put("cross",!compatible(id,((Number)before.get("job")).intValue()));
                diff.put("keybinding",shortcut.diff());
                String previewHash=CharacterAdminService.hash(List.of(request.cid(),request.revision(),diff));
                if(!save){con.rollback();return Map.of("diff",diff,"previewHash",previewHash,"name",before.get("name"));}
                if(!previewHash.equals(request.previewHash()))throw CharacterAdminService.failure("preview");
                if(value==null) {
                    try(PreparedStatement ps=con.prepareStatement("DELETE FROM skills WHERE characterid=? AND skillid=?")){ps.setInt(1,request.cid());ps.setInt(2,id);if(ps.executeUpdate()!=1)throw CharacterAdminService.failure("stale");}
                } else {
                    try(PreparedStatement ps=con.prepareStatement("INSERT INTO skills(characterid,skillid,skilllevel,masterlevel,expiration) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE skilllevel=VALUES(skilllevel),masterlevel=VALUES(masterlevel)")) {
                        ps.setInt(1,request.cid());ps.setInt(2,id);ps.setInt(3,((Number)value.get("level")).intValue());ps.setInt(4,((Number)value.get("masterLevel")).intValue());ps.setLong(5,((Number)value.get("expiration")).longValue());ps.executeUpdate();
                    }
                }
                for(var binding:bindings(before)) {
                    if(shortcut.bindings().contains(binding))continue;
                    try(PreparedStatement ps=con.prepareStatement("DELETE FROM keymap WHERE characterid=? AND `key`=?")){ps.setInt(1,request.cid());ps.setInt(2,SkillShortcutPolicy.number(binding,"key"));if(ps.executeUpdate()!=1)throw CharacterAdminService.failure("stale");}
                }
                for(var binding:shortcut.bindings()) {
                    if(bindings(before).contains(binding))continue;
                    try(PreparedStatement ps=con.prepareStatement("INSERT INTO keymap(characterid,`key`,`type`,`action`) VALUES(?,?,?,?)")){ps.setInt(1,request.cid());ps.setInt(2,SkillShortcutPolicy.number(binding,"key"));ps.setInt(3,SkillShortcutPolicy.number(binding,"type"));ps.setInt(4,SkillShortcutPolicy.number(binding,"action"));ps.executeUpdate();}
                }
                var after=state(con,request.cid(),false);
                var expected=new TreeMap<Integer,Map<String,Object>>();for(var row:rows(before))expected.put(((Number)row.get("id")).intValue(),row);
                if(value==null)expected.remove(id);else expected.put(id,value);
                if(!new ArrayList<>(expected.values()).equals(rows(after)))throw CharacterAdminService.failure("stale");
                if(!shortcut.bindings().equals(bindings(after)))throw CharacterAdminService.failure("stale");
                String audit=UUID.randomUUID().toString();
                try(PreparedStatement ps=con.prepareStatement("INSERT INTO admin_skill_edits(id,character_id,account_id,actor,before_state,after_state,changes) VALUES(?,?,?,?,?,?,?)")) {
                    ps.setString(1,audit);ps.setInt(2,request.cid());ps.setInt(3,account);ps.setString(4,actor);ps.setString(5,JSON.toJSONString(before));ps.setString(6,JSON.toJSONString(after));ps.setString(7,JSON.toJSONString(diff));ps.executeUpdate();
                }
                con.commit();return Map.of("auditId",audit);
            }catch(Exception e){con.rollback();throw e;}
        }
    }
    public List<Map<String,Object>> history(int cid) throws SQLException {
        List<Map<String,Object>> result=new ArrayList<>();
        try(Connection con=DatabaseConnection.getConnection();PreparedStatement ps=con.prepareStatement("SELECT id,actor,changes,created_at FROM admin_skill_edits WHERE character_id=? ORDER BY created_at DESC,id LIMIT 100")) {
            ps.setInt(1,cid);try(ResultSet rs=ps.executeQuery()){while(rs.next())result.add(Map.of("id",rs.getString(1),"actor",rs.getString(2),"changes",JSON.parseObject(rs.getString(3)),"createdAt",rs.getTimestamp(4)));}
        }return result;
    }
}
