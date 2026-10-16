// SPDX-License-Identifier: AGPL-3.0-only
var Stat = Java.type("org.gms.client.Stat");
var GameConstants = Java.type("org.gms.constants.game.GameConstants");
var InventoryType = Java.type("org.gms.client.inventory.InventoryType");
var PacketCreator = Java.type("org.gms.util.PacketCreator");
var Collections = Java.type("java.util.Collections");
var LoggerFactory = Java.type("org.slf4j.LoggerFactory");
var logger = LoggerFactory.getLogger("gm.self.stats");
var fields = [
    { label: "最大HP", getter: "getMaxHp", setter: "updateMaxHp", min: 50, max: 30000 },
    { label: "最大MP", getter: "getMaxMp", setter: "updateMaxMp", min: 5, max: 30000 },
    { label: "力量", getter: "getStr", setter: "setStr", min: 4, max: 32767, stat: Stat.STR },
    { label: "敏捷", getter: "getDex", setter: "setDex", min: 4, max: 32767, stat: Stat.DEX },
    { label: "智力", getter: "getInt", setter: "setInt", min: 4, max: 32767, stat: Stat.INT },
    { label: "运气", getter: "getLuk", setter: "setLuk", min: 4, max: 32767, stat: Stat.LUK },
    { label: "人气", getter: "getFame", kind: "fame", min: -30000, max: 30000 },
    { label: "未分配能力点AP", getter: "getRemainingAp", kind: "ap", min: 0, max: 32767 },
    { label: "当前职业未分配技能点SP", getter: "getRemainingSps", kind: "sp", min: 0, max: 32767 },
    { label: "增加金币", getter: "getMeso", kind: "meso", min: 1, max: 2147483647, additive: true },
    { label: "增加点券（账号共享）", getter: "nxCredit", kind: "cash", min: 1, max: 2147483647, additive: true }
];
var bagTypes = [InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP, InventoryType.ETC];
var bagNames = ["装备", "消耗", "设置", "其他"];
var oldSlots = [];
var stage = 0;
var chosen = -1;
var oldValue = 0;
var newValue = 0;
var amount = 0;
var playerId = -1;
var mapId = -1;
var jobId = -1;
var skillbook = 0;
var finished = false;

function stop(text) {
    finished = true;
    cm.sendOk(text);
    cm.dispose();
}

function permitted(p) {
    if (!p.isGM() || p.gmLevel() < 3) {
        stop("该功能需要GM3或更高权限。");
        return false;
    }
    if (!p.isAlive() || !p.isLoggedInWorld() || p.getTrade() !== null || p.getCashShop().isOpened() || p.getPlayerShop() !== null) {
        stop("请在正常游戏状态下使用，死亡、交易、摆摊或商城中不能修改。");
        return false;
    }
    return true;
}

function start() {
    var p = cm.getPlayer();
    if (!permitted(p)) return;
    playerId = p.getId();
    mapId = p.getMapId();
    jobId = p.getJob().getId();
    skillbook = GameConstants.getSkillBook(jobId);
    var text = "#e修改自身属性与资源#n\r\n角色：" + p.getName() + "\r\n";
    for (var i = 0; i < fields.length; i++) {
        text += "#L" + i + "#" + fields[i].label + "：" + (fields[i].additive ? "当前余额 " : "") + read(p, fields[i]) + "#l\r\n";
    }
    text += "\r\n#L100#背包一键扩容到最大（每栏96格）#l\r\n";
    text += "\r\n金币、点券输入增加数量；其余属性设置最终数值。HP/MP为基础上限，不含装备、技能加成。SP只修改当前职业技能册的剩余点数，不改变已学技能。修改后HP/MP自动回满。";
    cm.sendSimple(text);
}

function action(mode, type, selection) {
    if (finished) return;
    if (mode !== 1) { finished = true; cm.dispose(); return; }
    var p = cm.getPlayer();
    if (!permitted(p)) return;
    if (p.getId() !== playerId || p.getMapId() !== mapId || p.getJob().getId() !== jobId) {
        stop("角色、地图或职业已变化，请重新打开菜单。");
        return;
    }
    if (stage === 0) {
        if (selection === 100) {
            var text = "确认将当前角色背包扩容到最大？\r\n";
            var needsExpansion = false;
            for (var i = 0; i < bagTypes.length; i++) {
                oldSlots[i] = p.getInventory(bagTypes[i]).getSlotLimit();
                if (oldSlots[i] < 1 || oldSlots[i] > 96) { stop("背包容量异常，未作修改，请联系服主。"); return; }
                if (oldSlots[i] < 96) needsExpansion = true;
                text += bagNames[i] + "：" + oldSlots[i] + " → 96格\r\n";
            }
            if (!needsExpansion) { stop("所有背包栏已经是最大容量96格，无需扩容。"); return; }
            text += "现金栏固定96格，不改动物品或仓库。";
            stage = 3;
            cm.sendYesNo(text);
            return;
        }
        if (!Number.isInteger(selection) || selection < 0 || selection >= fields.length) {
            stop("无效的属性选项。");
            return;
        }
        chosen = selection;
        var f = fields[chosen];
        oldValue = read(p, f);
        if (f.additive && (!Number.isInteger(oldValue) || oldValue < 0 || oldValue >= f.max)) {
            stop("余额已到上限或异常，不能继续增加。"); return;
        }
        stage = 1;
        var maximum = f.additive ? f.max - oldValue : f.max;
        cm.sendGetNumber((f.additive ? f.label + "，请输入增加数量，当前余额：" : "设置" + f.label + "，当前值：") + oldValue + "\r\n范围：" + f.min + "～" + maximum,
            f.additive ? Math.min(10000, maximum) : Math.max(f.min, Math.min(f.max, oldValue)), f.min, maximum);
    } else if (stage === 1) {
        var f = fields[chosen];
        var maximum = f.additive ? f.max - oldValue : f.max;
        if (!Number.isInteger(selection) || selection < f.min || selection > maximum) {
            stop("数值超出允许范围，未作修改。");
            return;
        }
        amount = f.additive ? selection : 0;
        newValue = f.additive ? oldValue + amount : selection;
        stage = 2;
        cm.sendYesNo("确认修改角色「" + p.getName() + "」的" + f.label + "？\r\n" +
            (f.additive ? "当前余额：" + oldValue + "\r\n增加数量：" + amount + "\r\n增加后余额：" + newValue : oldValue + " → " + newValue) +
            "\r\n确认后保存，HP和MP自动回满。");
    } else if (stage === 2) {
        finished = true;
        var f = fields[chosen];
        if (read(p, f) !== oldValue) { stop("数值已发生变化，请重新打开菜单。"); return; }
        var oldHp = p.getHp();
        var oldMp = p.getMp();
        try {
            apply(p, f, newValue);
            if (read(p, f) !== newValue) throw new Error("VALUE_MISMATCH");
            p.healHpMp();
            if (!p.saveCharToDB(true, null, Collections.emptyList())) throw new Error("SAVE_FAILED");
            logger.info("GM_SELF_STATS cid=" + playerId + " field=" + f.getter + " skillbook=" + skillbook + " old=" + oldValue + " added=" + amount + " new=" + newValue);
            stop("修改已保存：" + f.label + " " + (f.additive ? amount + "，当前余额：" + read(p, f) : oldValue + " → " + read(p, f)) + "。\r\nHP和MP已回满。");
        } catch (error) {
            apply(p, f, oldValue);
            p.updateHpMp(oldHp, oldMp);
            logger.error("GM_SELF_STATS_FAILED cid=" + playerId + " field=" + f.getter + " error=" + String(error));
            stop("修改失败，已恢复修改前的属性。请查看服务端日志。");
        }
    } else if (stage === 3) {
        expandBags(p);
    }
}

function apply(p, f, value) {
    if (f.kind === "cash") { p.getCashShop().gainCash(1, value - read(p, f)); return; }
    if (f.kind === "fame") { p.gainFame(value - read(p, f)); return; }
    if (f.kind === "ap") { p.changeRemainingAp(value, false); return; }
    if (f.kind === "sp") { p.gainSp(value - read(p, f), skillbook, false); return; }
    if (f.kind === "meso") { p.gainMeso(value - read(p, f), false); return; }
    p[f.setter](value);
    if (f.stat) {
        p.recalcLocalStats();
        p.updateSingleStat(f.stat, value);
    }
}

function read(p, f) {
    if (f.kind === "cash") return p.getCashShop().getCash(1);
    if (f.kind === "sp") return p.getRemainingSps()[skillbook];
    return p[f.getter]();
}

function announceBags(p) {
    for (var i = 0; i < bagTypes.length; i++) {
        p.sendPacket(PacketCreator.updateInventorySlotLimit(bagTypes[i].getType(), p.getInventory(bagTypes[i]).getSlotLimit()));
    }
}

function expandBags(p) {
    finished = true;
    for (var i = 0; i < bagTypes.length; i++) {
        if (p.getInventory(bagTypes[i]).getSlotLimit() !== oldSlots[i]) {
            stop("背包容量已变化，请重新打开菜单。");
            return;
        }
    }
    try {
        for (var i = 0; i < bagTypes.length; i++) p.getInventory(bagTypes[i]).setSlotLimit(96);
        if (!p.saveCharToDB(true, null, Collections.emptyList())) throw new Error("SAVE_FAILED");
    } catch (error) {
        var restored = true;
        for (var i = 0; i < bagTypes.length; i++) {
            var inv = p.getInventory(bagTypes[i]);
            inv.lockInventory();
            try {
                // Shrinking deletes high-slot items in this server; never roll back an occupied new slot.
                var items = inv.list().toArray();
                var safe = true;
                for (var j = 0; j < items.length; j++) if (items[j].getPosition() > oldSlots[i]) safe = false;
                if (safe) inv.setSlotLimit(oldSlots[i]);
                else restored = false;
            } finally { inv.unlockInventory(); }
        }
        announceBags(p);
        logger.error("GM_BAG_EXPAND_FAILED cid=" + playerId + " restored=" + restored + " error=" + String(error));
        stop(restored ? "扩容保存失败，已恢复原容量，物品未改动。" : "扩容保存失败。新格子中已有物品，已保留相关栏容量以保护物品，请联系服主检查存档。");
        return;
    }
    logger.info("GM_BAG_EXPAND cid=" + playerId + " old=" + oldSlots.join(",") + " new=96,96,96,96");
    announceBags(p);
    stop("背包扩容已保存：装备、消耗、设置、其他栏均为96格。现金栏固定96格，物品未改动。");
}
