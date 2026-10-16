package org.gms.client.command.commands.gm6;

import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.superchar.SuperCharacterPlan;
import org.gms.server.superchar.SuperCharacterPolicy;
import org.gms.server.superchar.SuperCharacterStore;
import org.gms.util.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuperCharacterCommand extends Command {
    private static final Logger log=LoggerFactory.getLogger(SuperCharacterCommand.class);
    public SuperCharacterCommand(){setDescription(I18nUtil.getMessage("SuperCharacter.description"));}
    @Override public void execute(Client client,String[] args) {
        var chr=client.getPlayer();
        if(chr==null || chr.gmLevel()!=6)return;
        try {
            var options=SuperCharacterPolicy.parse(args);
            chr.withSuperCharacterState(()->{
                var plan=SuperCharacterPlan.build(chr,options);
                String slots=chr.getSlots(1)+"/"+chr.getSlots(2)+"/"+chr.getSlots(3)+"/"+chr.getSlots(4)+"/"+chr.getSlots(5);
                chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.preview",chr.getJob().getId(),options.career().job(),chr.getLevel(),plan.level(),
                    plan.attributes().get("str"),plan.attributes().get("dex"),plan.attributes().get("int"),plan.attributes().get("luk"),
                    plan.careerSkills(),options.gmSkills(),plan.gmSkills(),plan.changed(),plan.removed(),plan.added().size(),plan.skipped(),slots,
                    plan.available(),options.quality(),options.timeless(),options.special(),plan.noPreset()));
                chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.supplies",plan.supplies().superPotionAdded(),plan.supplies().allCureAdded()));
                if(!options.gmSkills().equals("none"))chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.clientLimit"));
                if(plan.careerSkillsSkipped())chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.skillsSkipped"));
                if(!options.confirm()){chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.confirm"));return null;}
                var result=new SuperCharacterStore().execute(chr,plan);
                chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.supplies",plan.supplies().superPotionAdded(),plan.supplies().allCureAdded()));
                chr.yellowMessage(I18nUtil.getMessage(result.reload()?"SuperCharacter.reload":"SuperCharacter.success",result.backupId(),options.key(),plan.level(),plan.added().size(),plan.skipped(),
                    plan.attributes().get("str"),plan.attributes().get("dex"),plan.attributes().get("int"),plan.attributes().get("luk"),plan.changed(),plan.removed()));
                if(plan.careerSkillsSkipped())chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.skillsSkipped"));
                return null;
            });
        }catch(IllegalArgumentException e) {
            String detail=e.getMessage();
            String reason=detail.split(":",2)[0];
            chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.rejected",I18nUtil.getMessage("SuperCharacter.reason."+reason)));
            if(reason.equals("equipped") || reason.equals("resource") || reason.equals("space"))
                chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.detail", detail.substring(Math.min(detail.length(),reason.length()+1))));
            chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.syntax"));
        }catch(Exception e) {
            log.error(I18nUtil.getLogMessage("SuperCharacter.failed"),chr.getId(),e);
            chr.yellowMessage(I18nUtil.getMessage("SuperCharacter.failed"));
        }
    }
}
