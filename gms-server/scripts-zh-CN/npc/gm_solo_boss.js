// SPDX-License-Identifier: AGPL-3.0-only
var Expedition = Java.type("org.gms.server.expeditions.Expedition");
var ExpeditionType = Java.type("org.gms.server.expeditions.ExpeditionType");
var ExpeditionBossLog = Java.type("org.gms.server.expeditions.ExpeditionBossLog");
var logger = Java.type("org.slf4j.LoggerFactory").getLogger("gm.solo.boss");
var bosses = [
    {name:"扎昆", event:"ZakumBattle", prefix:"Zakum", type:ExpeditionType.ZAKUM, entry:280030000,
        minLevel:50, suggested:150, minutes:120, maps:[280030000,211042400,211042300],
        mobs:[8800000,8800001,8800002,8800003,8800004,8800005,8800006,8800007,8800008,8800009,8800010],
        note:"自动激活祭坛，从本体和八臂开始，保留三阶段与通关流程。"},
    {name:"黑暗龙王", event:"HorntailBattle", prefix:"Horntail", type:ExpeditionType.HORNTAIL, entry:240060000,
        minLevel:100, suggested:180, minutes:120, maps:[240060000,240060100,240060200,240050600],
        mobs:[8810000,8810001,8810002,8810003,8810004,8810005,8810006,8810007,8810008,8810009,8810018,8810026],
        note:"从左头开始，击败左右头后进入本体图，按原流程击打水晶召唤龙王。"},
    {name:"品克缤", event:"PinkBeanBattle", prefix:"PinkBean", type:ExpeditionType.PINKBEAN, entry:270050100,
        minLevel:120, suggested:200, minutes:140, maps:[270050100,270050200,270050300],
        mobs:[8820000,8820001,8820002,8820003,8820004,8820005,8820006,8820019,8820020,8820021,8820022,8820023],
        note:"15秒后开始第一波石像，保留五波与最后本体的正式流程。"}
];
var stage = 0;
var selected = null;
var origin = -1;
var playerId = -1;
var channelId = -1;
var finished = false;

function stop(text) {
    finished = true;
    if (text) cm.sendOk(text);
    cm.dispose();
}

function permitted(p) {
    if (!p.isGM() || p.gmLevel() < 3) { stop("该功能需要GM3或更高权限。"); return false; }
    if (!p.isAlive() || !p.isLoggedInWorld() || p.getEventInstance() !== null || p.getTrade() !== null ||
        p.getPlayerShop() !== null || p.getCashShop().isOpened()) {
        stop("请在正常游戏状态使用，死亡、交易、摆摊、商城或已有副本中不能启动。"); return false;
    }
    return true;
}

function start() {
    var p = cm.getPlayer();
    if (!permitted(p)) return;
    origin = p.getMapId();
    playerId = p.getId();
    channelId = cm.getClient().getChannel();
    var text = "#eGM单人启动正式副本#n\r\n只带当前GM进入，不改变原队伍或全服人数设置。\r\n";
    for (var i = 0; i < bosses.length; i++) {
        text += "#L" + i + "#" + bosses[i].name + "（建议" + bosses[i].suggested + "级+，" + bosses[i].minutes + "分钟）#l\r\n";
    }
    text += "\r\n保留正式副本阶段、计时、掉落与退出流程。遵守本服每日远征次数设置。";
    cm.sendSimple(text);
}

function action(mode, type, selection) {
    if (finished) return;
    if (mode !== 1) { stop(); return; }
    var p = cm.getPlayer();
    if (!permitted(p)) return;
    if (p.getId() !== playerId || p.getMapId() !== origin || cm.getClient().getChannel() !== channelId) {
        stop("角色、地图或频道已变化，请重新打开菜单。"); return;
    }
    if (stage === 0) {
        if (!Number.isInteger(selection) || selection < 0 || selection >= bosses.length) { stop("无效的副本选项。"); return; }
        selected = bosses[selection];
        stage = 1;
        cm.sendYesNo("确认单人启动「" + selected.name + "」正式副本？\r\n" + selected.note +
            "\r\n最低等级：" + selected.minLevel + "；建议：" + selected.suggested + "级+。\r\n原队伍不会被带入或解散。免费，不需要入场材料；每日次数仍按本服设置执行。");
        return;
    }
    if (stage !== 1 || !selected) { stop(); return; }
    if (p.getLevel() < selected.minLevel) { stop("角色等级不足，需要" + selected.minLevel + "级。"); return; }
    var channel = cm.getClient().getChannelServer();
    var em = cm.getEventManager(selected.event);
    if (!em || em.getName() !== selected.event) { stop("正式副本脚本尚未加载，请联系服主检查：" + selected.event); return; }
    if (channel.getExpedition(selected.type) !== null || !em.getInstances().isEmpty()) {
        stop("该频道的副本正在使用或清理中，请稍后再试或更换频道。"); return;
    }
    var active = channel.getExpeditions().toArray();
    for (var i = 0; i < active.length; i++) {
        var memberIds = active[i].getMembers().keySet().toArray();
        for (var j = 0; j < memberIds.length; j++) if (Number(memberIds[j]) === playerId) {
            stop("你已登记其他远征队，请先退出原远征队。"); return;
        }
    }
    try {
        for (var i = 0; i < selected.maps.length; i++) {
            var map = channel.getMapFactory().getMap(selected.maps[i]);
            if (!map || !map.getPortal(0)) throw new Error("MAP_MISSING:" + selected.maps[i]);
        }
        if (selected.event === "ZakumBattle" && !channel.getMapFactory().getMap(211042300).getReactorById(2118002)) throw new Error("GATE_MISSING");
        var triggerMap = selected.event === "HorntailBattle" ? 240060200 : selected.entry;
        var triggerId = selected.event === "ZakumBattle" ? 2111001 : selected.event === "HorntailBattle" ? 2401000 : 2708000;
        if (!channel.getMapFactory().getMap(triggerMap).getReactorById(triggerId)) throw new Error("TRIGGER_MISSING");
        for (var i = 0; i < selected.mobs.length; i++) if (!cm.getMonsterLifeFactory(selected.mobs[i])) throw new Error("MOB_MISSING:" + selected.mobs[i]);
    } catch (error) {
        logger.error("GM_SOLO_BOSS_PREFLIGHT cid=" + playerId + " event=" + selected.event + " error=" + String(error));
        stop("副本地图、祭坛或怪物数据不完整，未启动，请查看服务端日志。"); return;
    }
    var expedition = new Expedition(p, selected.type, true, 1, 1);
    if (!ExpeditionBossLog.attemptBoss(playerId, channelId, expedition, false)) {
        stop("今日远征次数已用完或该频道不允许进入，未启动副本。"); return;
    }
    finished = true;
    if (!expedition.addChannelExpedition(channel)) { stop("该副本刚被其他人占用，未启动，请更换频道。"); return; }
    cm.dispose();
    try {
        p.saveLocationOnWarp();
        // Use the expedition overload: it supplies the actual channel to the original setup script.
        var started = em.startInstance(expedition);
        var eim = p.getEventInstance();
        if (!started || !eim || eim.getEm().getName() !== selected.event || eim.getLeaderId() !== playerId ||
            eim.getPlayerCount() !== 1 || p.getMapId() !== selected.entry || eim.isEventDisposed()) throw new Error("START_INCOMPLETE");
        eim.setProperty("gmSoloOwner", String(playerId));
        eim.setProperty("canJoin", "0");
        if (selected.event === "ZakumBattle") {
            var altar = p.getMap().getReactorById(2111001);
            if (!altar) throw new Error("ALTAR_MISSING");
            altar.hitReactor(cm.getClient());
            if (p.getMap().getMonsterById(8800000) === null || eim.getProperty("summoned") !== "true") throw new Error("ZAKUM_TRIGGER_FAILED");
        }
        logger.info("GM_SOLO_BOSS_STARTED cid=" + playerId + " event=" + selected.event + " channel=" + channelId + " instance=" + eim.getName());
        p.dropMessage(6, "已单人启动「" + selected.name + "」正式副本。" + selected.note);
    } catch (error) {
        var candidate = em.getInstance(selected.prefix + channelId);
        try {
            // Only clean up the instance reserved by this one-person expedition.
            if (candidate && (candidate.getLeaderId() === playerId || candidate.getPlayerCount() === 0)) {
                if (p.getEventInstance() === candidate) {
                    candidate.unregisterPlayer(p);
                    p.changeMap(origin, 0);
                }
                candidate.dispose();
            }
            if (channel.getExpedition(selected.type) === expedition) {
                expedition.removeChannelExpedition(channel);
                expedition.dispose(false);
            }
        } catch (cleanupError) {
            logger.error("GM_SOLO_BOSS_CLEANUP_FAILED cid=" + playerId + " event=" + selected.event + " error=" + String(cleanupError));
        }
        logger.error("GM_SOLO_BOSS_START_FAILED cid=" + playerId + " event=" + selected.event + " error=" + String(error));
        p.dropMessage(6, "副本启动失败，已尝试清理本次实例，请查看服务端日志。若启动已登记远征次数，该次数不会自动退回。");
    }
}
