/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.gms.model.pojo;

import org.gms.util.I18nUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.dao.entity.NewyearDO;
import org.gms.manager.ServerManager;
import org.gms.net.server.Server;
import org.gms.server.TimerManager;
import org.gms.service.NewYearCardService;
import org.gms.util.DatabaseConnection;
import org.gms.util.PacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.HOURS;

/**
 * @author Ronan - credits to Eric for showing the New Year opcodes and handler layout
 */
@Slf4j
@Data
public class NewYearCardRecord {
    private int id;

    private int senderId;
    private String senderName;
    private boolean senderDiscardCard;

    private int receiverId;
    private String receiverName;
    private boolean receiverDiscardCard;
    private boolean receiverReceivedCard;

    private String message;
    private long dateSent;
    private long dateReceived;

    private ScheduledFuture<?> sendTask;
    private static final NewYearCardService newYearCardService = ServerManager.getApplicationContext().getBean(NewYearCardService.class);

    public NewYearCardRecord(int senderId, String senderName, int receiverId, String receiverName, String message) {
        this.id = -1;

        this.senderId = senderId;
        this.senderName = senderName;
        this.senderDiscardCard = false;

        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.receiverDiscardCard = false;
        this.receiverReceivedCard = false;

        this.message = message;

        this.dateSent = System.currentTimeMillis();
        this.dateReceived = 0;
    }

    public void setExtraNewYearCardRecord(int id, boolean senderDiscardCard, boolean receiverDiscardCard, boolean receiverReceivedCard, long dateSent, long dateReceived) {
        this.id = id;
        this.senderDiscardCard = senderDiscardCard;
        this.receiverDiscardCard = receiverDiscardCard;
        this.receiverReceivedCard = receiverReceivedCard;

        this.dateSent = dateSent;
        this.dateReceived = dateReceived;
    }

    public static void saveNewYearCard(NewYearCardRecord newyear) {
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO newyear VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, newyear.senderId);
                ps.setString(2, newyear.senderName);
                ps.setInt(3, newyear.receiverId);
                ps.setString(4, newyear.receiverName);

                ps.setString(5, newyear.message);

                ps.setBoolean(6, newyear.senderDiscardCard);
                ps.setBoolean(7, newyear.receiverDiscardCard);
                ps.setBoolean(8, newyear.receiverReceivedCard);

                ps.setLong(9, newyear.dateSent);
                ps.setLong(10, newyear.dateReceived);

                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    newyear.id = rs.getInt(1);
                }
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    public static void updateNewYearCard(NewYearCardRecord newyear) {
        newyear.receiverReceivedCard = true;
        newyear.dateReceived = System.currentTimeMillis();

        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE newyear SET received=1, timereceived=? WHERE id=?")) {
                ps.setLong(1, newyear.dateReceived);
                ps.setInt(2, newyear.id);

                ps.executeUpdate();
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    public static NewYearCardRecord loadNewYearCard(int cardid) {
        NewYearCardRecord nyc = Server.getInstance().getNewYearCard(cardid);
        if (nyc != null) {
            return nyc;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM newyear WHERE id = ?")) {
                ps.setInt(1, cardid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        NewYearCardRecord newyear = new NewYearCardRecord(rs.getInt("senderid"), rs.getString("sendername"), rs.getInt("receiverid"), rs.getString("receivername"), rs.getString("message"));
                        newyear.setExtraNewYearCardRecord(rs.getInt("id"), rs.getBoolean("senderdiscard"), rs.getBoolean("receiverdiscard"), rs.getBoolean("received"), rs.getLong("timesent"), rs.getLong("timereceived"));

                        Server.getInstance().setNewYearCard(newyear);
                        return newyear;
                    }
                }
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        return null;
    }

    public static void loadPlayerNewYearCards(Character chr) {
        try {
            List<NewyearDO> newyearDOList = newYearCardService.loadPlayerNewYearCards(chr);
            newyearDOList.forEach(newyearDO -> {
                NewYearCardRecord newYearCardRecord = new NewYearCardRecord(newyearDO.getSenderid(), newyearDO.getSendername(),
                        newyearDO.getReceiverid(), newyearDO.getReceivername(), newyearDO.getMessage());
                newYearCardRecord.setExtraNewYearCardRecord(newyearDO.getId().intValue(), newyearDO.getSenderdiscard(),
                        newyearDO.getReceiverdiscard(), newyearDO.getReceived(), newyearDO.getTimesent(), newyearDO.getTimereceived());
                chr.addNewYearRecord(newYearCardRecord);
            });
        } catch (Exception e) {
            log.error(I18nUtil.getLogMessage("Localization.legacy.log.2a40f18ca69646e0"), e);
        }
    }

    public static void printNewYearRecords(Character chr) {
        chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.b2a92b274502c7ac") + chr.getNewYearRecords().size());

        for (NewYearCardRecord nyc : chr.getNewYearRecords()) {
            chr.dropMessage(5, "-------------------------------");

            chr.dropMessage(5, "Id: " + nyc.id);

            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.cf5fd9b3e5cb0009") + nyc.senderId);
            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.e6cc585951ab695a") + nyc.senderName);
            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.d023ff0a770775a1") + nyc.senderDiscardCard);

            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.9e29e804ad6b2be9") + nyc.receiverId);
            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.77ffaf9d8c9680dc") + nyc.receiverName);
            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.a890e73745c92e0b") + nyc.receiverDiscardCard);
            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.5db758d6e17ab2df") + nyc.receiverReceivedCard);

            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.6d2d6e90015608f5") + nyc.message);
            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.348b4d2d6d591547") + nyc.dateSent);
            chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.36af60f6ce1d10ec") + nyc.dateReceived);
        }
    }

    public void startNewYearCardTask() {
        if (sendTask != null) {
            return;
        }

        sendTask = TimerManager.getInstance().register(() -> {
            Server server = Server.getInstance();

            int world = server.getCharacterWorld(receiverId);
            if (world == -1) {
                sendTask.cancel(false);
                sendTask = null;

                return;
            }

            Character target = server.getWorld(world).getPlayerStorage().getCharacterById(receiverId);
            if (target != null && target.isLoggedInWorld()) {
                target.sendPacket(PacketCreator.onNewYearCardRes(target, NewYearCardRecord.this, 0xC, 0));
            }
        }, HOURS.toMillis(1));
    }

    public void stopNewYearCardTask() {
        if (sendTask != null) {
            sendTask.cancel(false);
            sendTask = null;
        }
    }

    private static void deleteNewYearCard(int id) {
        Server.getInstance().removeNewYearCard(id);

        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM newyear WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    public static void removeAllNewYearCard(boolean send, Character chr) {
        int cid = chr.getId();
        
        /* not truly needed since it's going to be hard removed from the DB
        String actor = (send ? "sender" : "receiver");
        
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE newyear SET " + actor + "id = 1, received = 0 WHERE " + actor + "id = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }
        } catch(SQLException sqle) {
            sqle.printStackTrace();
        }
        */

        Set<NewYearCardRecord> set = new HashSet<>(chr.getNewYearRecords());
        for (NewYearCardRecord nyc : set) {
            if (send) {
                if (nyc.senderId == cid) {
                    nyc.senderDiscardCard = true;
                    nyc.receiverReceivedCard = false;

                    chr.removeNewYearRecord(nyc);
                    deleteNewYearCard(nyc.id);

                    chr.getMap().broadcastMessage(PacketCreator.onNewYearCardRes(chr, nyc, 0xE, 0));

                    Character other = chr.getClient().getWorldServer().getPlayerStorage().getCharacterById(nyc.getReceiverId());
                    if (other != null && other.isLoggedInWorld()) {
                        other.removeNewYearRecord(nyc);
                        other.getMap().broadcastMessage(PacketCreator.onNewYearCardRes(other, nyc, 0xE, 0));

                        other.dropMessage(6, I18nUtil.getMessage("Localization.legacy.message.1f3e235075bd3c2f") + chr.getName() + I18nUtil.getMessage("Localization.legacy.message.3e5e48de63513438"));
                    }
                }
            } else {
                if (nyc.receiverId == cid) {
                    nyc.receiverDiscardCard = true;
                    nyc.receiverReceivedCard = false;

                    chr.removeNewYearRecord(nyc);
                    deleteNewYearCard(nyc.id);

                    chr.getMap().broadcastMessage(PacketCreator.onNewYearCardRes(chr, nyc, 0xE, 0));

                    Character other = chr.getClient().getWorldServer().getPlayerStorage().getCharacterById(nyc.getSenderId());
                    if (other != null && other.isLoggedInWorld()) {
                        other.removeNewYearRecord(nyc);
                        other.getMap().broadcastMessage(PacketCreator.onNewYearCardRes(other, nyc, 0xE, 0));

                        other.dropMessage(6, I18nUtil.getMessage("Localization.legacy.message.1f3e235075bd3c2f") + chr.getName() + I18nUtil.getMessage("Localization.legacy.message.3e5e48de63513438"));
                    }
                }
            }
        }
    }
}
