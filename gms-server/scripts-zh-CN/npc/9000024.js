var config;
var closed = false;

function start() {
    if (closed) return;
    var Files = Java.type('java.nio.file.Files');
    var Paths = Java.type('java.nio.file.Paths');
    config = JSON.parse(String(Files.readString(Paths.get('scripts-zh-CN/npc/fm-supply.zh-CN.json'))).replace(/^\uFEFF/, ''));
    if (cm.getPlayer().getMapId() !== config.map) {
        cm.sendOk(Java.type('org.gms.server.life.LifeFactory').getNPCDefaultTalk(cm.getNpc()));
        closed = true;
        cm.dispose();
        return;
    }
    syncPlacement();
    var shop = config.directShops.filter(function (entry) { return entry.npc === cm.getNpc(); })[0];
    closed = true;
    cm.dispose();
    if (shop) openFreshShop(shop.shopId);
}

function action(mode, type, selection) {
    if (closed) return;
    closed = true;
    cm.dispose();
}

function openFreshShop(id) {
    var shop = Java.type('org.gms.server.Shop').createFromDB(id, true);
    if (shop !== null) shop.sendShop(cm.getClient());
}

function syncPlacement() {
    var point = config.placement;
    if (!point || cm.getPlayer().getWorld() !== point.world) return;
    var channels = cm.getPlayer().getWorldServer().getChannels().iterator();
    var Point = Java.type('java.awt.Point');
    var LifeFactory = Java.type('org.gms.server.life.LifeFactory');
    var PacketCreator = Java.type('org.gms.util.PacketCreator');
    var mapKey = Java.type('java.lang.Integer').valueOf(String(config.map));
    while (channels.hasNext()) {
        var map = channels.next().getMapFactory().getMaps().get(mapKey);
        if (map === null) continue;
        var old = map.getNPCById(point.npc);
        if (old === null || (old.getPosition().x === point.x && old.getPosition().y === point.y && old.getFh() === point.fh)) continue;
        var npc = LifeFactory.getNPC(point.npc);
        npc.setPosition(new Point(point.x, point.y));
        npc.setCy(point.y);
        npc.setFh(point.fh);
        npc.setRx0(point.rx0);
        npc.setRx1(point.rx1);
        map.destroyNPC(point.npc);
        map.addMapObject(npc);
        map.broadcastMessage(PacketCreator.spawnNPC(npc));
    }
}
