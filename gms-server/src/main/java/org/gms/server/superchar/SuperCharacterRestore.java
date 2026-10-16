package org.gms.server.superchar;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.gms.util.I18nUtil;
import org.gms.manager.ServerManager;
import org.gms.property.ServiceProperty;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Offline owner utility. Exact post-operation snapshots are required, including instance IDs. */
public final class SuperCharacterRestore {
    private static final List<String> FIELDS = List.of("job", "level", "exp", "str", "dex", "int", "luk",
        "hp", "mp", "maxhp", "maxmp", "ap", "sp", "equipslots", "useslots", "setupslots", "etcslots");

    public static boolean unchanged(JSONObject current, JSONObject after) {
        // Normalize JDBC timestamps/numeric wrappers before structural comparison.
        return JSON.parseObject(JSON.toJSONString(current)).equals(JSON.parseObject(JSON.toJSONString(after)));
    }

    public static String restore(Connection con, String backupId, Path directory) throws Exception {
        UUID.fromString(backupId);
        con.setAutoCommit(false);
        try {
            int cid, account;
            JSONObject before, after;
            try (PreparedStatement ps = con.prepareStatement("SELECT characterid,accountid,before_state,after_state,restored_at FROM superchar_audit WHERE id=? FOR UPDATE")) {
                ps.setString(1, backupId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject(5) != null) throw new IllegalArgumentException("restore");
                    cid = rs.getInt(1); account = rs.getInt(2);
                    before = JSON.parseObject(rs.getString(3)); after = JSON.parseObject(rs.getString(4));
                }
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT c.accountid,a.loggedin FROM characters c JOIN accounts a ON a.id=c.accountid WHERE c.id=? FOR UPDATE")) {
                ps.setInt(1, cid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) != account || rs.getInt(2) != 0) throw new IllegalArgumentException("restore");
                }
            }
            JSONObject current = SuperCharacterStore.snapshot(con, cid);
            if (!unchanged(current, after)) throw new IllegalArgumentException("restore");
            JSONObject row = before.getJSONArray("character").getJSONObject(0);
            if (row.getIntValue("id") != cid || row.getIntValue("accountid") != account) throw new IllegalArgumentException("restore");
            String restoreId = UUID.randomUUID().toString();
            JSONObject recovery = new JSONObject();
            recovery.put("id", restoreId); recovery.put("restores", backupId);
            recovery.put("characterId", cid); recovery.put("accountId", account);
            recovery.put("createdAt", Instant.now().toString()); recovery.put("before", current);
            SuperCharacterStore.writeBackup(directory, restoreId, recovery);
            String assignment = String.join(",", FIELDS.stream().map(key -> "`" + key + "`=?").toList());
            try (PreparedStatement ps = con.prepareStatement("UPDATE characters SET " + assignment + " WHERE id=? AND accountid=?")) {
                int index = 1;
                for (String field : FIELDS) ps.setObject(index++, row.get(field));
                ps.setInt(index++, cid); ps.setInt(index, account);
                if (ps.executeUpdate() != 1) throw new SQLException("restore");
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM skills WHERE characterid=?")) {
                ps.setInt(1, cid); ps.executeUpdate();
            }
            for (Object value : before.getJSONArray("skills")) {
                JSONObject skill = (JSONObject) value;
                skill.put("characterid", cid);
                insert(con, "skills", skill);
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE i,e FROM inventoryitems i LEFT JOIN inventoryequipment e USING(inventoryitemid) WHERE i.characterid=? AND i.type=1")) {
                ps.setInt(1, cid); ps.executeUpdate();
            }
            for (Object value : before.getJSONArray("items")) {
                JSONObject item = (JSONObject) value;
                if (item.getIntValue("characterid") != cid || item.getIntValue("type") != 1) throw new IllegalArgumentException("restore");
                insert(con, "inventoryitems", item);
            }
            for (Object value : before.getJSONArray("equipment")) insert(con, "inventoryequipment", (JSONObject) value);
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO superchar_restore_audit (id,backupid,characterid,accountid,before_state,restored_state) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, restoreId); ps.setString(2, backupId); ps.setInt(3, cid); ps.setInt(4, account);
                ps.setString(5, JSON.toJSONString(current));
                ps.setString(6, JSON.toJSONString(SuperCharacterStore.snapshot(con, cid))); ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE superchar_audit SET restored_at=CURRENT_TIMESTAMP WHERE id=? AND restored_at IS NULL")) {
                ps.setString(1, backupId);
                if (ps.executeUpdate() != 1) throw new SQLException("restore");
            }
            con.commit();
            return restoreId;
        } catch (Exception e) {
            con.rollback();
            throw e;
        }
    }

    private static void insert(Connection con, String table, JSONObject row) throws SQLException {
        // Column names come only from server-authored database audit snapshots, never CLI arguments.
        List<String> columns = row.keySet().stream().sorted().toList();
        if (columns.isEmpty() || columns.stream().anyMatch(key -> !key.matches("[A-Za-z][A-Za-z0-9_]*"))) throw new SQLException("restore");
        String names = String.join(",", columns.stream().map(key -> "`" + key + "`").toList());
        String placeholders = String.join(",", columns.stream().map(key -> "?").toList());
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO " + table + " (" + names + ") VALUES (" + placeholders + ")")) {
            for (int i = 0; i < columns.size(); i++) ps.setObject(i + 1, row.get(columns.get(i)));
            ps.executeUpdate();
        }
    }

    public static void main(String[] args) throws Exception {
        if (ServerManager.getApplicationContext() == null) {
            GenericApplicationContext context=new GenericApplicationContext();
            ServiceProperty property=new ServiceProperty(); property.setLanguage("zh-CN");
            context.getBeanFactory().registerSingleton("serviceProperty",property);
            for(String kind:List.of("message","log","exception")) {
                ResourceBundleMessageSource messages=new ResourceBundleMessageSource();
                messages.setBasename("i18n/"+kind); messages.setDefaultEncoding("UTF-8");
                context.getBeanFactory().registerSingleton(kind+"Source",messages);
            }
            context.refresh(); new ServerManager().setApplicationContext(context);
        }
        if (args.length != 2 || !"server-stopped".equals(args[1])) throw new IllegalArgumentException(I18nUtil.getMessage("SuperCharacter.reason.restore"));
        try (Connection con = DriverManager.getConnection(System.getenv("SUPERCHAR_JDBC"), System.getenv("SUPERCHAR_USER"), System.getenv("SUPERCHAR_PASSWORD"))) {
            String id = restore(con, args[0], Path.of("backups", "superchar"));
            System.out.println(I18nUtil.getMessage("SuperCharacter.restored", id));
        }
    }
    private SuperCharacterRestore() {}
}
