/*
 * This file is part of the BeiDou MapleStory Server.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
var status = -1;
var selected = null;
var mapId = null;
var finished = false;
var bosses = [
    { name: "红蜗牛王", id: 2220000, level: 25 },
    { name: "树妖王", id: 3220000, level: 40 },
    { name: "大宇", id: 3220001, level: 45 },
    { name: "绿水灵王", id: 9300003, level: 45 },
    { name: "歇尔夫", id: 4220000, level: 50 },
    { name: "浮士德", id: 5220002, level: 55 },
    { name: "巨型蜈蚣", id: 5220004, level: 55 },
    { name: "巨居蟹", id: 5220000, level: 60 },
    { name: "提莫", id: 5220003, level: 65 },
    { name: "蘑菇王", id: 6130101, level: 65 },
    { name: "谢尔德", id: 5090000, level: 65 },
    { name: "阿丽莎乐", id: 9300012, level: 65, party: true },
    { name: "书生鬼", id: 6090003, level: 70 },
    { name: "雪山魔女", id: 6090001, level: 70 },
    { name: "多尔", id: 6220000, level: 70 },
    { name: "僵尸蘑菇王", id: 6300005, level: 70 },
    { name: "朱诺", id: 6220001, level: 70 },
    { name: "青竹武士", id: 6090002, level: 75 },
    { name: "黑山老妖", id: 6090000, level: 80 },
    { name: "九尾狐", id: 7220001, level: 80 },
    { name: "肯德熊", id: 7220000, level: 80 },
    { name: "蝙蝠怪", id: 8130100, level: 85 },
    { name: "妖怪禅师", id: 7220002, level: 85 },
    { name: "生气的老海盗", id: 9300105, level: 85, party: true },
    { name: "艾利杰", id: 8220000, level: 90 },
    { name: "吉米拉", id: 8220002, level: 95 },
    { name: "驮狼雪人", id: 8220001, level: 100 },
    { name: "蓝蘑菇王", id: 8220007, level: 100 },
    { name: "蝙蝠魔", id: 8150000, level: 110 },
    { name: "陆陆猫", id: 6090004, level: 110, party: true },
    { name: "死灵骑士", id: 9400549, level: 115 },
    { name: "火焰龙", id: 8180000, level: 115 },
    { name: "天鹰", id: 8180001, level: 115 },
    { name: "多多", id: 8220004, level: 130 },
    { name: "大海兽", id: 8220003, level: 130, party: true },
    { name: "大脚怪", id: 9400575, level: 130, party: true },
    { name: "皮亚奴斯", id: 8510000, level: 135, party: true },
    { name: "仙人玩偶", id: 5090001, level: 140 },
    { name: "玄冰独角兽", id: 8220005, level: 140 },
    { name: "帕普拉图斯的座钟", id: 8500001, level: 140, party: true },
    { name: "天狗", id: 9400014, level: 140, party: true },
    { name: "雷卡", id: 8220006, level: 150 },
    { name: "盔甲武士", id: 9400405, level: 150, party: true },
    { name: "女老板", id: 9400121, level: 150, party: true },
    { name: "扎昆（本体＋八臂）", id: 8800000, kind: "zakum", level: 150, party: true },
    { name: "保镖A", id: 9400112, level: 175, party: true },
    { name: "保镖B", id: 9400113, level: 175, party: true },
    { name: "黑暗龙王（完整）", id: 8810018, kind: "horntail", level: 180, party: true },
    { name: "大头头", id: 9400300, level: 190, party: true },
    { name: "品克缤（本体）", id: 8820001, level: 200, party: true }
];

function levelNote(boss) {
    return "建议" + boss.level + "级+" + (boss.party ? "，组队" : "");
}

function start() {
    action(1, 0, 0);
}

function stop(message) {
    finished = true;
    if (message) cm.sendOk(message);
    cm.dispose();
}

function action(mode, type, selection) {
    if (finished) return;
    if (mode !== 1) { stop(); return; }
    var player = cm.getPlayer();
    if (!player.isGM() || player.gmLevel() < 3) {
        stop("该功能需要GM3或更高权限。");
        return;
    }
    if (!player.isAlive()) { stop("请先复活角色。"); return; }
    status++;
    if (status === 0) {
        mapId = player.getMap().getId();
        var text = "#e在当前地图召唤首领（由易到难）#n\r\n";
        text += "建议等级按普通装备估算，非入场限制。\r\n\r\n";
        for (var i = 0; i < bosses.length; i++) {
            text += "#L" + i + "#" + bosses[i].name + " #b（" + levelNote(bosses[i]) + "）#k#l\r\n";
        }
        cm.sendSimple(text);
        return;
    }
    if (status === 1) {
        if (!Number.isInteger(selection) || selection < 0 || selection >= bosses.length) {
            stop("无效的BOSS选项。");
            return;
        }
        selected = bosses[selection];
        cm.sendYesNo("在当前地图（" + mapId + "）召唤#r" + selected.name + "#k？\r\n#b" + levelNote(selected) + "#k");
        return;
    }
    if (status !== 2 || !selected) { stop(); return; }
    var map = player.getMap();
    if (map.getId() !== mapId) { stop("地图已经改变，请重新打开菜单。"); return; }
    var ids = [selected.id];
    if (selected.kind === "zakum") {
        for (var arm = 8800003; arm <= 8800010; arm++) ids.push(arm);
        ids.push(8800001, 8800002);
    } else if (selected.kind === "horntail") {
        ids.push(8810026);
        for (var part = 8810002; part <= 8810009; part++) ids.push(part);
    }
    var monsters = [];
    for (var j = 0; j < ids.length; j++) {
        if (map.getMonsterById(ids[j]) !== null) {
            stop("当前地图已经有该BOSS或部件，请先清理后再召唤。");
            return;
        }
        var monster = cm.getMonsterLifeFactory(ids[j]);
        if (!monster) { stop("缺少怪物数据：" + ids[j] + "，未召唤。"); return; }
        monsters.push(monster);
    }
    // Use the existing multipart lifecycle instead of spawning the Horntail soul alone.
    var position = player.getPosition();
    var Point = Java.type("java.awt.Point");
    if (map.getPointBelow(new Point(position.x, position.y - 14)) === null) {
        stop("当前位置没有可用地面，请站稳后重新召唤。");
        return;
    }
    finished = true;
    if (selected.kind === "horntail") {
        map.spawnHorntailOnGroundBelow(position);
    } else if (selected.kind === "zakum") {
        map.spawnFakeMonsterOnGroundBelow(monsters[0], position);
        for (var k = 1; k < 9; k++) map.spawnMonsterOnGroundBelow(monsters[k], position);
    } else {
        map.spawnMonsterOnGroundBelow(monsters[0], position);
    }
    cm.sendOk("已在当前地图召唤：" + selected.name + "。");
    cm.dispose();
}
