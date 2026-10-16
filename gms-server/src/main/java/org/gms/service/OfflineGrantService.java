package org.gms.service;

import com.alibaba.fastjson2.JSON;
import com.mybatisflex.core.paginate.Page;
import org.gms.client.Character;
import org.gms.client.Job;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.Pet;
import org.gms.constants.inventory.ItemConstants;
import org.gms.exception.BizException;
import org.gms.model.dto.ChrOnlineListReqDTO;
import org.gms.model.dto.GiveResourceReqDTO;
import org.gms.model.dto.GrantCharacterDTO;
import org.gms.net.server.Server;
import org.gms.net.server.world.World;
import org.gms.server.CashShop;
import org.gms.server.ItemInformationProvider;
import org.gms.util.BasePageUtil;
import org.gms.util.CashIdGenerator;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Service
public class OfflineGrantService {
    public Page<GrantCharacterDTO> list(ChrOnlineListReqDTO request) throws SQLException {
        List<GrantCharacterDTO> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT c.id,c.world,c.name,c.map,c.job,c.level,c.gm,(SELECT COUNT(*) FROM admin_resource_grants g WHERE g.character_id=c.id AND g.status='pending') AS pending_count FROM characters c WHERE c.world=? ORDER BY c.id")) {
            ps.setInt(1, request.getWorld());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GrantCharacterDTO row = new GrantCharacterDTO();
                    row.setId(rs.getInt("id"));
                    row.setWorld(rs.getInt("world"));
                    row.setName(rs.getString("name"));
                    Character online = findOnline(row.getId(), row.getWorld());
                    row.setOnline(online != null);
                    row.setMap(online == null ? rs.getInt("map") : online.getMapId());
                    row.setJob(online == null ? rs.getInt("job") : online.getJob().getId());
                    Job job = Job.getById(row.getJob());
                    row.setJobName(job == null ? "" : job.getName());
                    row.setLevel(online == null ? rs.getInt("level") : online.getLevel());
                    row.setGm(online == null ? rs.getInt("gm") : online.gmLevel());
                    row.setPendingCount(rs.getInt("pending_count"));
                    rows.add(row);
                }
            }
        }
        return BasePageUtil.create(rows, request)
                .filter(row -> (request.getId() == null || row.getId() == request.getId())
                        && (request.getName() == null || row.getName().contains(request.getName()))
                        && (request.getMap() == null || row.getMap() == request.getMap())).page();
    }

    private Character findOnline(int cid, int worldId) {
        World world = Server.getInstance().getWorld(worldId);
        return world == null ? null : world.getPlayerStorage().getCharacterById(cid);
    }

    public boolean queueIfOffline(GiveResourceReqDTO data, String envelopeId) throws SQLException {
        if (data == null || data.getPlayerId() == null || data.getType() == null) throw invalid();
        if (data.getPlayerId() == 0) return false;
        if (data.getWorldId() == null || data.getWorldId() < 0 || data.getPlayerId() < 1) throw invalid();
        String key = data.getDeliveryId() == null ? envelopeId : data.getDeliveryId();
        try { if (!UUID.fromString(key).toString().equals(key)) throw invalid(); } catch (Exception e) { throw invalid(); }
        String payload = JSON.toJSONString(data);
        try (Connection con = DatabaseConnection.getConnection()) {
            // A retried offline request must not become a second online grant after login.
            if (existing(con, key, data.getPlayerId(), payload)) return true;
            if (findOnline(data.getPlayerId(), data.getWorldId()) != null) return false;
            validate(data);
            int accountId;
            try (PreparedStatement ps = con.prepareStatement("SELECT accountid FROM characters WHERE id=? AND world=?")) {
                ps.setInt(1, data.getPlayerId());
                ps.setInt(2, data.getWorldId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new BizException(I18nUtil.getExceptionMessage("UNKNOWN_CHARACTER"));
                    accountId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO admin_resource_grants(request_id,character_id,account_id,payload) VALUES(?,?,?,?)")) {
                ps.setString(1, key);
                ps.setInt(2, data.getPlayerId());
                ps.setInt(3, accountId);
                ps.setString(4, payload);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!existing(con, key, data.getPlayerId(), payload)) throw e;
            }
        }
        return true;
    }

    private boolean existing(Connection con, String key, int cid, String payload) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT character_id,payload FROM admin_resource_grants WHERE request_id=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (rs.getInt(1) != cid || !JSON.parseObject(rs.getString(2)).equals(JSON.parseObject(payload))) throw invalid();
                return true;
            }
        }
    }

    public static void validate(GiveResourceReqDTO data) {
        int type = data.getType() == null ? -1 : data.getType();
        if (type < 0 || type > 6) throw new BizException(I18nUtil.getExceptionMessage("Grant.offlineType"));
        if (type != 6 && (data.getQuantity() == null || data.getQuantity() < 1)) throw invalid();
        if (type >= 5) {
            if (data.getId() == null || data.getId() < 1) throw invalid();
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            if (ii.getName(data.getId()) == null) throw new BizException(I18nUtil.getExceptionMessage("ITEM_NOT_FOUND"));
            boolean equipment = ItemConstants.getInventoryType(data.getId()) == InventoryType.EQUIP;
            if (equipment != (type == 6) || (equipment && ii.getEquipById(data.getId()) == null)) throw invalid();
            if (type == 5 && data.getQuantity() > Short.MAX_VALUE) throw invalid();
            if (data.getExpire() != null && (data.getExpire() < -1 || data.getExpire() > 5256000)) throw invalid();
            if (data.getUpgradeSlot() != null && data.getUpgradeSlot() < 0) throw invalid();
            Short[] stats = {data.getStr(),data.getDex(),data.get_int(),data.getLuk(),data.getHp(),data.getMp(),data.getPAtk(),data.getMAtk(),data.getPDef(),data.getMDef(),data.getAcc(),data.getAvoid(),data.getHands(),data.getSpeed(),data.getJump()};
            for (Short stat : stats) if (stat != null && stat < 0) throw invalid();
        }
    }

    private static BizException invalid() {
        return new BizException(I18nUtil.getExceptionMessage("Grant.invalid"));
    }

    public int deliverAtLogin(Character chr) throws SQLException {
        List<Long> delivered = new ArrayList<>();
        chr.beginOfflineGrantInitialization();
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
            List<Map.Entry<Long, GiveResourceReqDTO>> rewards = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT id,payload FROM admin_resource_grants WHERE character_id=? AND account_id=? AND status='pending' ORDER BY COALESCE(attempted_at,created_at),id LIMIT 100 FOR UPDATE")) {
                ps.setInt(1, chr.getId());
                ps.setInt(2, chr.getAccountId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rewards.add(Map.entry(rs.getLong(1), JSON.parseObject(rs.getString(2), GiveResourceReqDTO.class)));
                }
            }
            for (Map.Entry<Long, GiveResourceReqDTO> reward : rewards) {
                if (apply(chr, reward.getValue(), con)) delivered.add(reward.getKey());
                else try (PreparedStatement ps = con.prepareStatement("UPDATE admin_resource_grants SET attempted_at=CURRENT_TIMESTAMP(6) WHERE id=?")) {
                    ps.setLong(1, reward.getKey());
                    ps.executeUpdate();
                }
            }
            if (delivered.isEmpty()) { con.commit(); return 0; }
            // The character is still private to the login handler; failure discards it.
            chr.setLoggedIn(true);
            if (!chr.saveCharToDB(true, con, delivered)) throw new SQLException(I18nUtil.getExceptionMessage("Grant.saveFailed"));
            con.commit();
            return delivered.size();
            } catch (SQLException | RuntimeException e) {
                try { con.rollback(); } catch (SQLException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                // A lost commit acknowledgement must never cause a persisted pet ID to be reused.
                throw e;
            }
        } finally {
            chr.endOfflineGrantInitialization();
        }
    }

    static boolean fitsBalance(int current, int quantity) {
        return quantity > 0 && (long) current + quantity <= Integer.MAX_VALUE;
    }

    private boolean apply(Character chr, GiveResourceReqDTO data, Connection con) throws SQLException {
        int type = data.getType();
        if (type <= 2) {
            int cashType = switch (type) { case 1 -> CashShop.NX_PREPAID; case 2 -> CashShop.MAPLE_POINT; default -> CashShop.NX_CREDIT; };
            if (!fitsBalance(chr.getCashShop().getCash(cashType), data.getQuantity())) return false;
            chr.getCashShop().gainCash(cashType, data.getQuantity());
        } else if (type == 3) {
            if (!fitsBalance(chr.getMeso(), data.getQuantity())) return false;
            chr.gainMeso(data.getQuantity(), false);
        } else if (type == 4) {
            return chr.gainOfflineGrantExp(data.getQuantity());
        } else if (type == 6) {
            Inventory inv = chr.getInventory(InventoryType.EQUIP);
            if (inv.isFull()) return false;
            Equip equip = equipment(data);
            if (inv.addItem(equip) < 1) throw new SQLException(I18nUtil.getExceptionMessage("Grant.slotUnavailable"));
        } else {
            return addItems(chr, data, con);
        }
        return true;
    }

    static Equip equipment(GiveResourceReqDTO data) {
        Equip equip = (Equip) ItemInformationProvider.getInstance().getEquipById(data.getId());
        if (equip == null) throw invalid();
        equip = (Equip) equip.copy();
        equip.setQuantity((short) 1);
        set(equip, data.getStr(), Equip::setStr); set(equip, data.getDex(), Equip::setDex);
        set(equip, data.get_int(), Equip::setInt); set(equip, data.getLuk(), Equip::setLuk);
        set(equip, data.getHp(), Equip::setHp); set(equip, data.getMp(), Equip::setMp);
        set(equip, data.getPAtk(), Equip::setWatk); set(equip, data.getMAtk(), Equip::setMatk);
        set(equip, data.getPDef(), Equip::setWdef); set(equip, data.getMDef(), Equip::setMdef);
        set(equip, data.getAcc(), Equip::setAcc); set(equip, data.getAvoid(), Equip::setAvoid);
        set(equip, data.getHands(), Equip::setHands); set(equip, data.getSpeed(), Equip::setSpeed);
        set(equip, data.getJump(), Equip::setJump); set(equip, data.getUpgradeSlot(), Equip::setUpgradeSlots);
        equip.setExpiration(data.getExpire() != null && data.getExpire() > 0 ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(data.getExpire()) : -1);
        return equip;
    }

    private static <T> void set(Equip equip, T value, BiConsumer<Equip, T> setter) {
        if (value != null) setter.accept(equip, value);
    }

    private boolean addItems(Character chr, GiveResourceReqDTO data, Connection con) throws SQLException {
        int id = data.getId();
        boolean pet = ItemConstants.isPet(id);
        Inventory inv = chr.getInventory(ItemConstants.getInventoryType(id));
        int quantity = pet ? 1 : data.getQuantity();
        int slotMax = pet ? 1 : ItemInformationProvider.getInstance().getSlotMax(chr.getClient(), id);
        if (slotMax <= 0) throw invalid();
        if (ItemConstants.isRechargeable(id) && quantity > slotMax) return false;
        List<Item> stacks = pet || ItemConstants.isRechargeable(id) ? List.of() : inv.listById(id).stream()
                .filter(item -> Objects.equals(item.getOwner(), "WAdmin") && item.getFlag() == 0 && item.getExpiration() == -1).toList();
        long capacity = stacks.stream().mapToLong(item -> Math.max(0, slotMax - item.getQuantity())).sum();
        int free = inv.getSlotLimit() - (int) inv.list().stream().filter(item -> item.getPosition() > 0).count();
        if (quantity > capacity + (long) Math.max(0, free) * slotMax) return false;
        int petId = -1;
        if (pet) {
            petId = CashIdGenerator.generateCashId();
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO pets(petid,name,level,closeness,fullness,summoned,flag) VALUES(?,?,1,0,100,0,0)")) {
                ps.setInt(1, petId);
                ps.setString(2, ItemInformationProvider.getInstance().getName(id));
                ps.executeUpdate();
            }
        }
        for (Item stack : stacks) {
            int add = Math.min(quantity, Math.max(0, slotMax - stack.getQuantity()));
            stack.setQuantity((short) (stack.getQuantity() + add));
            quantity -= add;
        }
        while (quantity > 0) {
            int add = Math.min(quantity, slotMax);
            Item item = pet ? new Item(id, (short) 0, (short) add, Pet.loadFromDb(id, (short) 0, petId, con))
                    : new Item(id, (short) 0, (short) add);
            item.setOwner("WAdmin");
            item.setExpiration(pet ? System.currentTimeMillis() + TimeUnit.DAYS.toMillis(data.getQuantity()) : -1);
            if (inv.addItem(item) < 1) throw new SQLException(I18nUtil.getExceptionMessage("Grant.slotUnavailable"));
            quantity -= add;
        }
        return true;
    }

    public List<Map<String, Object>> history(int cid) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id,payload,status,created_at,delivered_at FROM admin_resource_grants WHERE character_id=? ORDER BY id DESC LIMIT 200")) {
            ps.setInt(1, cid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong(1));
                    row.put("resource", JSON.parseObject(rs.getString(2)));
                    row.put("status", rs.getString(3));
                    row.put("createdAt", rs.getTimestamp(4));
                    row.put("deliveredAt", rs.getTimestamp(5));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
