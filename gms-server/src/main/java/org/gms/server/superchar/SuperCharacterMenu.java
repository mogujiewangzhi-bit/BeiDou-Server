package org.gms.server.superchar;

import org.gms.client.command.commands.gm6.SuperCharacterCommand;
import org.gms.scripting.npc.NPCConversationManager;
import org.gms.scripting.npc.NPCScriptManager;
import org.gms.util.I18nUtil;

/** NPC preview is read-only; execution retains the command's ordinary state checks. */
public final class SuperCharacterMenu {
    public static String preview(NPCConversationManager menu, String key) {
        var chr = menu.getPlayer();
        return chr.withSuperCharacterState(() -> {
            var plan = SuperCharacterPlan.previewMenu(menu, key);
            String preview = I18nUtil.getMessage("SuperCharacter.menuPreview", chr.getName(), chr.getJob().getId(),
                plan.level(), plan.attributes().get("str"), plan.attributes().get("dex"),
                plan.attributes().get("int"), plan.attributes().get("luk"), plan.careerSkills(),
                plan.removed(), plan.added().size(), plan.skipped(), I18nUtil.getMessage("SuperCharacter.career." + key));
            preview += "\r\n\r\n" + I18nUtil.getMessage("SuperCharacter.supplies",plan.supplies().superPotionAdded(),plan.supplies().allCureAdded());
            if (plan.careerSkillsSkipped()) preview += "\r\n\r\n" + I18nUtil.getMessage("SuperCharacter.skillsSkipped");
            return preview;
        });
    }

    public static void confirm(NPCConversationManager menu, String key) {
        var client = menu.getClient();
        if (client.getPlayer() == null || client.getPlayer().gmLevel() != 6)
            throw new IllegalArgumentException("permission");
        if (NPCScriptManager.getInstance().getCM(client) != menu || menu.getNpc() != 9900001
            || !"gm_superchar".equals(menu.getScriptName()))
            throw new IllegalArgumentException("busy");
        SuperCharacterPolicy.parse(new String[]{key, "timeless=true", "confirm"});
        menu.dispose();
        // Rebuild under the command's locks, after closing the NPC conversation.
        new SuperCharacterCommand().execute(client, new String[]{key, "timeless=true", "confirm"});
    }

    private SuperCharacterMenu() {}
}
