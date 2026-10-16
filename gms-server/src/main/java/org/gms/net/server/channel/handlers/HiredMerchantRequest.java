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
import org.gms.client.inventory.ItemFactory;
import org.gms.constants.game.GameConstants;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.maps.HiredMerchant;
import org.gms.server.maps.MapObject;
import org.gms.server.maps.MapObjectType;
import org.gms.server.maps.PlayerShop;
import org.gms.server.maps.Portal;
import org.gms.util.PacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * @author XoticStory
 */
public final class HiredMerchantRequest extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(HiredMerchantRequest.class);

    @Override
    public final void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();

        try {
            for (MapObject mmo : chr.getMap().getMapObjectsInRange(chr.getPosition(), 23000, Arrays.asList(MapObjectType.HIRED_MERCHANT, MapObjectType.PLAYER))) {
                if (mmo instanceof Character mc) {

                    PlayerShop shop = mc.getPlayerShop();
                    if (shop != null && shop.isOwner(mc)) {
                        chr.sendPacket(PacketCreator.getMiniRoomError(13));
                        return;
                    }
                } else {
                    chr.sendPacket(PacketCreator.getMiniRoomError(13));
                    return;
                }
            }

            Point cpos = chr.getPosition();
            Portal portal = chr.getMap().findClosestTeleportPortal(cpos);
            if (portal != null && portal.getPosition().distance(cpos) < 120.0) {
                chr.sendPacket(PacketCreator.getMiniRoomError(10));
                return;
            }
        } catch (Exception e) {
            log.warn(I18nUtil.getLogMessage("Localization.legacy.log.acf5f3496eb41477"), chr.getName(), e);
        }

        if (!GameConstants.isFreeMarketRoom(chr.getMapId())) {
            chr.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.1daee7627d63d8ae"));
            return;
        }

        HiredMerchant existing = c.getWorldServer().getHiredMerchant(chr.getId());
        if (existing != null) {
            if (existing.isPublished()) {
                chr.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.2a2bc2cff5175472") + existing.getChannel() + ".");
                return;
            }

            log.warn(I18nUtil.getLogMessage("Localization.legacy.log.475bd8564c6c5dfa"),
                    chr.getName(), existing.getChannel(), chr.getHiredMerchant() == existing);
            try {
                chr.setHiredMerchant(existing);
                existing.forceClose();
            } catch (RuntimeException ex) {
                log.error(I18nUtil.getLogMessage("Localization.legacy.log.ed487c02108fa3db"),
                        chr.getName(), existing.getChannel(), ex);
                chr.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.f07ecf925844174d"));
                chr.sendPacket(PacketCreator.enableActions());
                return;
            }

            try {
                if (!ItemFactory.MERCHANT.loadItems(chr.getId(), false).isEmpty() || chr.getMerchantMeso() != 0) {
                    chr.dropMessage(5, I18nUtil.getMessage("Localization.legacy.message.7df259a2d4639f21"));
                    chr.sendPacket(PacketCreator.retrieveFirstMessage());
                } else {
                    chr.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.188370af325db299"));
                    chr.sendPacket(PacketCreator.enableActions());
                }
            } catch (SQLException ex) {
                log.error(I18nUtil.getLogMessage("Localization.legacy.log.3aace93f6f59c12b"), chr.getName(), ex);
                chr.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.7cddf112551a9074"));
                chr.sendPacket(PacketCreator.enableActions());
            }
            return;
        }

        if (chr.hasMerchant()) {
            chr.dropMessage(1, I18nUtil.getMessage("Localization.legacy.message.bb3565f328bd14a6"));
            return;
        }

        try {
            if (ItemFactory.MERCHANT.loadItems(chr.getId(), false).isEmpty() && chr.getMerchantMeso() == 0) {
                c.sendPacket(PacketCreator.hiredMerchantBox());
            } else {
                chr.sendPacket(PacketCreator.retrieveFirstMessage());
            }
        } catch (SQLException ex) {
            log.error(I18nUtil.getLogMessage("Localization.legacy.log.8dcd2051527c367d"), chr.getName(), ex);
        }
    }
}
