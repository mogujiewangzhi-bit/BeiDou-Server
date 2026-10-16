// Self-only GM6 strengthening. No changes are made until the Yes response.
var step = 0;
var finished = false;
var selectedKey = null;
var careers = ["hero", "paladin", "darkknight", "fp", "il", "bishop", "bowmaster", "marksman",
    "nightlord", "shadower", "buccaneer", "corsair", "dawnwarrior", "blazewizard", "windarcher",
    "nightwalker", "thunderbreaker", "aran", "evan", "gm", "supergm"];
var SuperCharacterMenu = Java.type("org.gms.server.superchar.SuperCharacterMenu");
var I18n = Java.type("org.gms.util.I18nUtil");

function start() {
    if (finished) return;
    if (cm.getPlayer().gmLevel() !== 6) {
        fail("permission");
        return;
    }
    step = 1;
    var text = "#e顶级角色强化#n\r\n\r\n";
    for (var index = 0; index < careers.length; index++) {
        text += "#L" + index + "#" + I18n.getMessage("SuperCharacter.career." + careers[index]) + "#l\r\n";
    }
    cm.sendSimple(text);
}

function action(mode, type, selection) {
    if (finished) return;
    if (mode !== 1) {
        finished = true;
        cm.dispose();
        return;
    }
    try {
        if (step === 1 && type === 4 && selection >= 0 && selection < careers.length
            && Math.floor(selection) === selection) {
            selectedKey = careers[selection];
            var preview = SuperCharacterMenu.preview(cm, selectedKey);
            step = 2;
            cm.sendYesNo(preview);
        } else if (step === 2 && type === 1 && selectedKey !== null) {
            finished = true;
            SuperCharacterMenu.confirm(cm, selectedKey);
        } else {
            fail("input");
        }
    } catch (error) {
        reject(error);
    }
}

function reject(error) {
    var detail = String(error.message || error);
    var match = detail.match(/\b(permission|busy|config|equipped|resource|space|input)\b/);
    if (match) fail(match[1]);
    else {
        cm.sendOk(I18n.getMessage("SuperCharacter.failed"));
        cm.dispose();
        finished = true;
    }
}

function fail(reason) {
    finished = true;
    cm.sendOk(I18n.getMessage("SuperCharacter.rejected", I18n.getMessage("SuperCharacter.reason." + reason)));
    cm.dispose();
}
