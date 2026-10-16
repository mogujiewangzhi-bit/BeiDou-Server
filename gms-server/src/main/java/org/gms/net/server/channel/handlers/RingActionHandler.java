/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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
package org.gms.net.server.channel.handlers;

import org.gms.util.I18nUtil;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Ring;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.client.processor.npc.DueyProcessor;
import org.gms.constants.id.ItemId;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.net.server.channel.Channel;
import org.gms.net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gms.scripting.event.EventInstanceManager;
import org.gms.server.ItemInformationProvider;
import org.gms.service.NoteService;
import org.gms.util.DatabaseConnection;
import org.gms.util.PacketCreator;
import org.gms.util.Pair;
import org.gms.util.packets.WeddingPackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Jvlaple
 * @author Ronan - major overhaul on Ring handling mechanics
 * @author Drago (Dragohe4rt) - on Wishlist
 */
public final class RingActionHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(RingActionHandler.class);

    private final NoteService noteService;

    public RingActionHandler(NoteService noteService) {
        this.noteService = noteService;
    }

    private static int getEngagementBoxId(int useItemId) {
        return switch (useItemId) {
            case ItemId.ENGAGEMENT_BOX_MOONSTONE -> ItemId.EMPTY_ENGAGEMENT_BOX_MOONSTONE;
            case ItemId.ENGAGEMENT_BOX_STAR -> ItemId.EMPTY_ENGAGEMENT_BOX_STAR;
            case ItemId.ENGAGEMENT_BOX_GOLDEN -> ItemId.EMPTY_ENGAGEMENT_BOX_GOLDEN;
            case ItemId.ENGAGEMENT_BOX_SILVER -> ItemId.EMPTY_ENGAGEMENT_BOX_SILVER;
            default -> ItemId.CARAT_RING_BASE + (useItemId - ItemId.CARAT_RING_BOX_BASE);
        };
    }

    public static void sendEngageProposal(final Client c, final String name, final int itemid) {
        final int newBoxId = getEngagementBoxId(itemid);
        final Character target = c.getChannelServer().getPlayerStorage().getCharacterByName(name);
        final Character source = c.getPlayer();

        // TODO: get the correct packet bytes for these popups
        if (source.isMarried()) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.b9c9c96a82ed1991"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (source.getPartnerId() > 0) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.4b7167aa3ae528a0"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (source.getMarriageItemId() > 0) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.00079c77484296c0"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (target == null) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.76ff2cf9c68ef97c") + name + I18nUtil.getMessage("Localization.legacy.message.e498e7b54410b462"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (target == source) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.03f1e28fe8699e67"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (target.getLevel() < 50) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.706a559dd579db49"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (source.getLevel() < 50) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.ee0189caf6404e8f"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (!target.getMap().equals(source.getMap())) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.e83389e51e48805a"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (!source.haveItem(itemid) || itemid < ItemId.ENGAGEMENT_BOX_MIN || itemid > ItemId.ENGAGEMENT_BOX_MAX) {
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (target.isMarried()) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.f521a6b772239ad4"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (target.getPartnerId() > 0 || target.getMarriageItemId() > 0) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.5e9ce50ccb2ddb09"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (target.haveWeddingRing()) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.c28d97eda0627249"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (source.haveWeddingRing()) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.68d99b4165bb7a89"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (target.getGender() == source.getGender()) {
            source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.2842ab31d3329d20") + (source.getGender() == 1 ? I18nUtil.getMessage("Localization.legacy.message.0d248e82c62c9386") : I18nUtil.getMessage("Localization.legacy.message.9f165139a8c2894a")) + "!");
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (!InventoryManipulator.checkSpace(c, newBoxId, 1, "")) {
            source.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.ea4f8753c0f7c222"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        } else if (!InventoryManipulator.checkSpace(target.getClient(), newBoxId + 1, 1, "")) {
            source.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.5e58a6ffaecda353"));
            source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));
            return;
        }

        source.setMarriageItemId(itemid);
        target.sendPacket(WeddingPackets.onMarriageRequest(source.getName(), source.getId()));
    }

    private static void eraseEngagementOffline(int characterId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            eraseEngagementOffline(characterId, con);
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    private static void eraseEngagementOffline(int characterId, Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE characters SET marriageItemId=-1, partnerId=-1 WHERE id=?")) {
            ps.setInt(1, characterId);
            ps.executeUpdate();
        }
    }

    private static void breakEngagementOffline(int characterId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT marriageItemId FROM characters WHERE id=?")) {
            ps.setInt(1, characterId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int marriageItemId = rs.getInt("marriageItemId");

                    if (marriageItemId > 0) {
                        try (PreparedStatement ps2 = con.prepareStatement("UPDATE inventoryitems SET expiration=0 WHERE itemid=? AND characterid=?")) {
                            ps2.setInt(1, marriageItemId);
                            ps2.setInt(2, characterId);

                            ps2.executeUpdate();
                        }
                    }
                }
            }

            eraseEngagementOffline(characterId, con);
        } catch (SQLException ex) {
            log.error(I18nUtil.getLogMessage("Localization.legacy.log.e945b85610be7800"), ex);
        }
    }

    private synchronized static void breakMarriage(Character chr) {
        int partnerid = chr.getPartnerId();
        if (partnerid <= 0) {
            return;
        }

        chr.getClient().getWorldServer().deleteRelationship(chr.getId(), partnerid);
        Ring.removeRing(chr.getMarriageRing());

        Character partner = chr.getClient().getWorldServer().getPlayerStorage().getCharacterById(partnerid);
        if (partner == null) {
            eraseEngagementOffline(partnerid);
        } else {
            partner.dropMessage(5, chr.getName() + I18nUtil.getMessage("Localization.legacy.message.4ecd865a5ffcf247"));

            //partner.sendPacket(Wedding.OnMarriageResult((byte) 0)); ok, how to gracefully unengage someone without the need to cc?
            partner.sendPacket(WeddingPackets.OnNotifyWeddingPartnerTransfer(0, 0));
            resetRingId(partner);
            partner.setPartnerId(-1);
            partner.setMarriageItemId(-1);
            partner.setMarriageRing(null);
            org.gms.server.quest.medal.OutstandingCitizenMedal.refreshEligibility(partner);
        }

        chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.84c2fd5fac1af388") + Character.getNameById(partnerid) + ".");

        //chr.sendPacket(Wedding.OnMarriageResult((byte) 0));
        chr.sendPacket(WeddingPackets.OnNotifyWeddingPartnerTransfer(0, 0));
        resetRingId(chr);
        chr.setPartnerId(-1);
        chr.setMarriageItemId(-1);
        chr.setMarriageRing(null);
        org.gms.server.quest.medal.OutstandingCitizenMedal.refreshEligibility(chr);
    }

    private static void resetRingId(Character player) {
        int ringitemid = player.getMarriageRing().getItemId();

        Item it = player.getInventory(InventoryType.EQUIP).findById(ringitemid);
        if (it == null) {
            it = player.getInventory(InventoryType.EQUIPPED).findById(ringitemid);
        }

        if (it != null) {
            Equip eqp = (Equip) it;
            eqp.setRingId(-1);
        }
    }

    private synchronized static void breakEngagement(Character chr) {
        int partnerid = chr.getPartnerId();
        int marriageitemid = chr.getMarriageItemId();

        chr.getClient().getWorldServer().deleteRelationship(chr.getId(), partnerid);

        Character partner = chr.getClient().getWorldServer().getPlayerStorage().getCharacterById(partnerid);
        if (partner == null) {
            breakEngagementOffline(partnerid);
        } else {
            partner.dropMessage(5, chr.getName() + I18nUtil.getMessage("Localization.legacy.message.8c4ac511a26adfd1"));

            int partnerMarriageitemid = marriageitemid + ((chr.getGender() == 0) ? 1 : -1);
            if (partner.haveItem(partnerMarriageitemid)) {
                InventoryManipulator.removeById(partner.getClient(), InventoryType.ETC, partnerMarriageitemid, (short) 1, false, false);
            }

            //partner.sendPacket(Wedding.OnMarriageResult((byte) 0)); ok, how to gracefully unengage someone without the need to cc?
            partner.sendPacket(WeddingPackets.OnNotifyWeddingPartnerTransfer(0, 0));
            partner.setPartnerId(-1);
            partner.setMarriageItemId(-1);
        }

        if (chr.haveItem(marriageitemid)) {
            InventoryManipulator.removeById(chr.getClient(), InventoryType.ETC, marriageitemid, (short) 1, false, false);
        }
        chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.42ca7876c0eb98de") + Character.getNameById(partnerid) + ".");

        //chr.sendPacket(Wedding.OnMarriageResult((byte) 0));
        chr.sendPacket(WeddingPackets.OnNotifyWeddingPartnerTransfer(0, 0));
        chr.setPartnerId(-1);
        chr.setMarriageItemId(-1);
    }

    public static void breakMarriageRing(Character chr, final int wItemId) {
        final InventoryType type = InventoryType.getByType((byte) (wItemId / 1000000));
        final Item wItem = chr.getInventory(type).findById(wItemId);
        final boolean weddingToken = (wItem != null && type == InventoryType.ETC && wItemId / 10000 == 403);
        final boolean weddingRing = (wItem != null && wItemId / 10 == 111280);

        if (weddingRing) {
            if (chr.getPartnerId() > 0) {
                breakMarriage(chr);
            }

            chr.getMap().disappearingItemDrop(chr, chr, wItem, chr.getPosition());
        } else if (weddingToken) {
            if (chr.getPartnerId() > 0) {
                breakEngagement(chr);
            }

            chr.getMap().disappearingItemDrop(chr, chr, wItem, chr.getPosition());
        }
    }

    public static void giveMarriageRings(Character player, Character partner, int marriageRingId) {
        Pair<Integer, Integer> rings = Ring.createRing(marriageRingId, player, partner);
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        Item ringObj = ii.getEquipById(marriageRingId);
        Equip ringEqp = (Equip) ringObj;
        ringEqp.setRingId(rings.getLeft());
        player.setMarriageRing(Ring.loadFromDb(rings.getLeft()));
        InventoryManipulator.addFromDrop(player.getClient(), ringEqp, false, -1);
        player.broadcastMarriageMessage();

        ringObj = ii.getEquipById(marriageRingId);
        ringEqp = (Equip) ringObj;
        ringEqp.setRingId(rings.getRight());
        partner.setMarriageRing(Ring.loadFromDb(rings.getRight()));
        InventoryManipulator.addFromDrop(partner.getClient(), ringEqp, false, -1);
        partner.broadcastMarriageMessage();
        org.gms.server.quest.medal.OutstandingCitizenMedal.refreshEligibility(player);
        org.gms.server.quest.medal.OutstandingCitizenMedal.refreshEligibility(partner);
    }

    @Override
    public final void handlePacket(InPacket p, Client c) {
        byte mode = p.readByte();
        String name;
        byte slot;
        switch (mode) {
            case 0: // Send Proposal
                sendEngageProposal(c, p.readString(), p.readInt());
                break;

            case 1: // Cancel Proposal
                if (c.getPlayer().getMarriageItemId() / 1000000 != 4) {
                    c.getPlayer().setMarriageItemId(-1);
                }
                break;

            case 2: // Accept/Deny Proposal
                final boolean accepted = p.readByte() > 0;
                name = p.readString();
                final int id = p.readInt();

                final Character source = c.getWorldServer().getPlayerStorage().getCharacterByName(name);
                final Character target = c.getPlayer();

                if (source == null) {
                    target.sendPacket(PacketCreator.enableActions());
                    return;
                }

                final int itemid = source.getMarriageItemId();
                if (target.getPartnerId() > 0 || source.getId() != id || itemid <= 0 || !source.haveItem(itemid) || source.getPartnerId() > 0 || !source.isAlive() || !target.isAlive()) {
                    target.sendPacket(PacketCreator.enableActions());
                    return;
                }

                if (accepted) {
                    final int newItemId = getEngagementBoxId(itemid);
                    if (!InventoryManipulator.checkSpace(c, newItemId, 1, "") || !InventoryManipulator.checkSpace(source.getClient(), newItemId, 1, "")) {
                        target.sendPacket(PacketCreator.enableActions());
                        return;
                    }

                    try {
                        InventoryManipulator.removeById(source.getClient(), InventoryType.USE, itemid, 1, false, false);

                        int marriageId = c.getWorldServer().createRelationship(source.getId(), target.getId());
                        source.setPartnerId(target.getId()); // engage them (new marriageitemid, partnerid for both)
                        target.setPartnerId(source.getId());

                        source.setMarriageItemId(newItemId);
                        target.setMarriageItemId(newItemId + 1);

                        InventoryManipulator.addById(source.getClient(), newItemId, (short) 1);
                        InventoryManipulator.addById(c, (newItemId + 1), (short) 1);

                        source.sendPacket(WeddingPackets.OnMarriageResult(marriageId, source, false));
                        target.sendPacket(WeddingPackets.OnMarriageResult(marriageId, source, false));

                        source.sendPacket(WeddingPackets.OnNotifyWeddingPartnerTransfer(target.getId(), target.getMapId()));
                        target.sendPacket(WeddingPackets.OnNotifyWeddingPartnerTransfer(source.getId(), source.getMapId()));
                    } catch (Exception e) {
                        log.error(I18nUtil.getLogMessage("Localization.legacy.log.d829666774d3f9c6"), e);
                    }
                } else {
                    source.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.e9b2d1cc00800348"));
                    source.sendPacket(WeddingPackets.OnMarriageResult((byte) 0));

                    source.setMarriageItemId(-1);
                }
                break;

            case 3: // Break Engagement
                breakMarriageRing(c.getPlayer(), p.readInt());
                break;

            case 5: // Invite %s to Wedding
                name = p.readString();
                int marriageId = p.readInt();
                slot = p.readByte(); // this is an int

                int itemId;
                try {
                    itemId = c.getPlayer().getInventory(InventoryType.ETC).getItem(slot).getItemId();
                } catch (NullPointerException npe) {
                    c.sendPacket(PacketCreator.enableActions());
                    return;
                }

                if ((itemId != ItemId.INVITATION_CHAPEL && itemId != ItemId.INVITATION_CATHEDRAL) || !c.getPlayer().haveItem(itemId)) {
                    c.sendPacket(PacketCreator.enableActions());
                    return;
                }

                String groom = c.getPlayer().getName();
                String bride = Character.getNameById(c.getPlayer().getPartnerId());
                int guest = Character.getIdByName(name);
                if (groom == null || bride == null || groom.equals("") || bride.equals("") || guest <= 0) {
                    c.getPlayer().dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.76ff2cf9c68ef97c") + name + "!");
                    return;
                }

                try {
                    World wserv = c.getWorldServer();
                    Pair<Boolean, Boolean> registration = wserv.getMarriageQueuedLocation(marriageId);

                    if (registration != null) {
                        if (wserv.addMarriageGuest(marriageId, guest)) {
                            boolean cathedral = registration.getLeft();
                            int newItemId = cathedral ? ItemId.RECEIVED_INVITATION_CATHEDRAL : ItemId.RECEIVED_INVITATION_CHAPEL;

                            Channel cserv = c.getChannelServer();
                            int resStatus = cserv.getWeddingReservationStatus(marriageId, cathedral);
                            if (resStatus > 0) {
                                long expiration = cserv.getWeddingTicketExpireTime(resStatus + 1);

                                String baseMessage = I18nUtil.getMessage("Localization.legacy.message.13da1ce49438e584").formatted(groom, bride);
                                Character guestChr = c.getWorldServer().getPlayerStorage().getCharacterById(guest);
                                if (guestChr != null && InventoryManipulator.checkSpace(guestChr.getClient(), newItemId, 1, "") && InventoryManipulator.addById(guestChr.getClient(), newItemId, (short) 1, expiration)) {
                                    guestChr.dropMessage(6, I18nUtil.getMessage("Localization.legacy.message.d8dcaff517910c99").formatted(baseMessage));
                                } else {
                                    String dueyMessage = baseMessage + I18nUtil.getMessage("Localization.legacy.message.47dd88a029f81f01");
                                    if (guestChr != null && guestChr.isLoggedInWorld()) {
                                        guestChr.dropMessage(6, I18nUtil.getMessage("Localization.legacy.message.d8dcaff517910c99").formatted(dueyMessage));
                                    } else {
                                        noteService.sendNormal(dueyMessage, groom, name);
                                    }

                                    Item weddingTicket = new Item(newItemId, (short) 0, (short) 1);
                                    weddingTicket.setExpiration(expiration);

                                    DueyProcessor.dueyCreatePackage(weddingTicket, 0, groom, guest);
                                }
                            } else {
                                c.getPlayer().dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.5988d79d820dbc93"));
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "'" + name + I18nUtil.getMessage("Localization.legacy.message.90f2672b1d6f7882"));
                        }
                    } else {
                        c.getPlayer().dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.10ea4ee6a0482aff") + name + I18nUtil.getMessage("Localization.legacy.message.1a41960847a0fecb"));
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    return;
                }

                c.getAbstractPlayerInteraction().gainItem(itemId, (short) -1);
                break;

            case 6: // Open Wedding Invitation
                slot = (byte) p.readInt();
                int invitationid = p.readInt();

                if (invitationid == ItemId.RECEIVED_INVITATION_CHAPEL || invitationid == ItemId.RECEIVED_INVITATION_CATHEDRAL) {
                    Item item = c.getPlayer().getInventory(InventoryType.ETC).getItem(slot);
                    if (item == null || item.getItemId() != invitationid) {
                        c.sendPacket(PacketCreator.enableActions());
                        return;
                    }

                    // collision case: most soon-to-come wedding will show up
                    Pair<Integer, Integer> coupleId = c.getWorldServer().getWeddingCoupleForGuest(c.getPlayer().getId(), invitationid == ItemId.RECEIVED_INVITATION_CATHEDRAL);
                    if (coupleId != null) {
                        int groomId = coupleId.getLeft(), brideId = coupleId.getRight();
                        c.sendPacket(WeddingPackets.sendWeddingInvitation(Character.getNameById(groomId), Character.getNameById(brideId)));
                    }
                }

                break;

            case 9:
                try {
                    // By -- Dragoso (Drago)
                    // Groom and Bride's Wishlist

                    Character player = c.getPlayer();

                    EventInstanceManager eim = player.getEventInstance();
                    if (eim != null) {
                        boolean isMarrying = (player.getId() == eim.getIntProperty("groomId") || player.getId() == eim.getIntProperty("brideId"));

                        if (isMarrying) {
                            int amount = p.readShort();
                            if (amount > 10) {
                                amount = 10;
                            }

                            String wishlistItems = "";
                            for (int i = 0; i < amount; i++) {
                                String s = p.readString();
                                wishlistItems += (s + "\r\n");
                            }

                            String wlKey;
                            if (player.getId() == eim.getIntProperty("groomId")) {
                                wlKey = "groomWishlist";
                            } else {
                                wlKey = "brideWishlist";
                            }

                            if (eim.getProperty(wlKey).contentEquals("")) {
                                eim.setProperty(wlKey, wishlistItems);
                            }
                        }
                    }
                } catch (NumberFormatException nfe) {
                }

                break;

            default:
                log.warn(I18nUtil.getLogMessage("Localization.legacy.log.d31ec0df27863892"), p);
                break;
        }

        c.sendPacket(PacketCreator.enableActions());
    }
}
