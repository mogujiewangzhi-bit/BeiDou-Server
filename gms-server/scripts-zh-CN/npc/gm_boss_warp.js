// SPDX-License-Identifier: AGPL-3.0-only
var Point = Java.type("java.awt.Point");
var bosses = [
    {name:"蘑菇王", id:6130101, map:100000005, level:65},
    {name:"谢尔德", id:5090000, map:103000105, level:65},
    {name:"雪山魔女", id:6090001, map:211010000, level:70},
    {name:"僵尸蘑菇王", id:6300005, map:105070002, level:70},
    {name:"书生鬼", id:6090003, map:222010300, level:70},
    {name:"黑山老妖", id:6090000, map:211041100, level:80},
    {name:"蝙蝠怪", id:8130100, map:105090900, level:85},
    {name:"驮狼雪人", id:8220001, map:211040101, level:100},
    {name:"陆陆猫", id:6090004, map:261020200, level:110, party:true},
    {name:"死灵骑士", id:9400549, map:682000001, level:115},
    {name:"火焰龙", id:8180000, map:240020401, level:115},
    {name:"天鹰", id:8180001, map:240020101, level:115},
    {name:"多多", id:8220004, map:270010500, level:130},
    {name:"大脚怪", id:9400575, map:610010100, level:130, party:true},
    {name:"鱼王（皮亚奴斯）", id:8510000, map:230040420, level:135, party:true, extras:[8520000]},
    {name:"仙人玩偶", id:5090001, map:250020300, level:140},
    {name:"玄冰独角兽", id:8220005, map:270020500, level:140},
    {name:"闹钟（帕普拉图斯）", id:8500001, map:220080001, level:140, party:true, battle:true, extras:[8500000,8500002]},
    {name:"天狗", id:9400014, map:800020130, level:140, party:true},
    {name:"雷卡", id:8220006, map:270030500, level:150},
    {name:"女老板", id:9400121, map:801040003, level:150, party:true},
    {name:"扎昆（本体＋八臂）", id:8800000, map:280030000, level:150, party:true, battle:true, kind:"zakum"},
    {name:"黑暗龙王（完整）", id:8810018, map:240060200, level:180, party:true, battle:true, kind:"horntail"},
    {name:"品克缤（本体练习）", id:8820001, map:270050100, level:200, party:true, battle:true, extras:[8820000,8820002]}
];
var stage = 0;
var selected = null;
var summon = false;
var origin = -1;
var finished = false;

function stop(text) {
    finished = true;
    if (text) cm.sendOk(text);
    cm.dispose();
}

function permitted() {
    var p = cm.getPlayer();
    if (!p.isGM() || p.gmLevel() < 3) { stop("该功能需要GM3或更高权限。"); return false; }
    if (!p.isAlive() || !p.isLoggedInWorld() || p.getTrade() !== null || p.getPlayerShop() !== null || p.getCashShop().isOpened() || p.getEventInstance() !== null) {
        stop("请在正常游戏状态使用，死亡、交易、摆摊、商城或副本中不能传送。"); return false;
    }
    return true;
}

function start() {
    if (!permitted()) return;
    origin = cm.getPlayer().getMapId();
    var text = "#e首领战斗地图（由易到难）#n\r\nGM传送免费。建议等级按普通装备估算，不是等级限制。\r\n";
    for (var i = 0; i < bosses.length; i++) {
        var b = bosses[i];
        text += "#L" + i + "#" + b.name + " #b（建议" + b.level + "级+" + (b.party ? "，组队" : "") + "）#k#l\r\n";
    }
    cm.sendSimple(text);
}

function idsFor(b) {
    var ids = [b.id];
    if (b.extras) ids = ids.concat(b.extras);
    if (b.kind === "zakum") { for (var id = 8800001; id <= 8800010; id++) ids.push(id); }
    if (b.kind === "horntail") {
        ids.push(8810026);
        for (var id = 8810000; id <= 8810009; id++) ids.push(id);
    }
    return ids;
}

function loadMap() {
    try { return cm.getClient().getChannelServer().getMapFactory().getMap(selected.map); }
    catch (error) { return null; }
}

function action(mode, type, selection) {
    if (finished) return;
    if (mode !== 1) { stop(); return; }
    if (!permitted()) return;
    var p = cm.getPlayer();
    if (p.getMapId() !== origin) { stop("地图已经变化，请重新打开菜单。"); return; }
    if (stage === 0) {
        if (!Number.isInteger(selection) || selection < 0 || selection >= bosses.length) { stop("无效的BOSS选项。"); return; }
        selected = bosses[selection];
        stage = 1;
        cm.sendSimple(selected.name + "\r\n目标战斗图：" + selected.map + "\r\n" +
            (selected.battle ? "只传送不会自动启动正式副本。" : "野外首领可能处于刷新等待中。") +
            "\r\n#L0#只传送到战斗图#l\r\n#L1#传送并召唤首领（GM练习）#l\r\n召唤不会重置地图或清怪，不执行正式副本任务/阶段；品克缤仅召唤本体。");
        return;
    }
    if (stage === 1) {
        if (selection !== 0 && selection !== 1) { stop("无效的传送方式。"); return; }
        summon = selection === 1;
        stage = 2;
        cm.sendYesNo("确认前往" + selected.name + "的战斗图（" + selected.map + "）" + (summon ? "并召唤首领" : "") + "？\r\n建议" + selected.level + "级+。免费，不消耗金币。");
        return;
    }
    if (stage !== 2 || !selected) { stop(); return; }
    var map = loadMap();
    if (!map || !map.getPortal(0)) { stop("该战斗地图暂不可用，未传送或扣费。地图ID：" + selected.map); return; }
    var ids = idsFor(selected);
    var existing = false;
    var monsters = {};
    if (summon) {
        if (map.getEventInstance() !== null) { stop("目标地图正用于副本，请使用正式入口或更换频道。"); return; }
        var occupants = map.getCharacters().toArray();
        for (var i = 0; i < occupants.length; i++) if (occupants[i].getId() !== p.getId()) {
            stop("目标地图有其他玩家，为避免打扰，不能自动召唤。可选择只传送或更换频道。"); return;
        }
        for (var i = 0; i < ids.length; i++) {
            if (map.getMonsterById(ids[i]) !== null) existing = true;
            monsters[ids[i]] = cm.getMonsterLifeFactory(ids[i]);
            if (!monsters[ids[i]]) { stop("缺少怪物数据：" + ids[i] + "，未传送或召唤。"); return; }
        }
    }
    finished = true;
    p.saveLocationOnWarp();
    cm.warp(selected.map, 0);
    if (p.getMapId() !== selected.map) { stop("传送未完成，未召唤BOSS。"); return; }
    if (!summon) { stop(); return; }
    map = p.getMap();
    for (var i = 0; i < ids.length; i++) if (map.getMonsterById(ids[i]) !== null) existing = true;
    if (existing) { stop("已到达战斗图，地图已有该BOSS或阶段部件，未重复召唤。"); return; }
    var position = p.getPosition();
    if (map.getPointBelow(new Point(position.x, position.y - 14)) === null) { stop("已到达战斗图，但当前位置无可用地面，未召唤。可站稳后使用GM召唤菜单。"); return; }
    if (selected.kind === "horntail") map.spawnHorntailOnGroundBelow(position);
    else if (selected.kind === "zakum") {
        map.spawnFakeMonsterOnGroundBelow(monsters[8800000], position);
        for (var arm = 8800003; arm <= 8800010; arm++) map.spawnMonsterOnGroundBelow(monsters[arm], position);
    } else map.spawnMonsterOnGroundBelow(monsters[selected.id], position);
    stop("已直达战斗图并召唤" + selected.name + "。这是GM练习，不是正式副本流程。");
}
