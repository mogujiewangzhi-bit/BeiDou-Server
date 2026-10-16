package org.gms.server.superchar;

import org.gms.client.Character;
import org.gms.client.Job;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.config.GameConfig;
import org.gms.constants.game.GameConstants;
import org.gms.model.pojo.SkillEntry;
import org.gms.provider.Data;
import org.gms.provider.DataProviderFactory;
import org.gms.provider.DataTool;
import org.gms.provider.wz.WZFiles;
import org.gms.scripting.npc.NPCScriptManager;
import org.gms.scripting.npc.NPCConversationManager;
import org.gms.server.ItemInformationProvider;
import org.gms.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SuperCharacterPlan(SuperCharacterPolicy.Options options, int level, Map<String,Integer> attributes,
                                Map<Skill,SkillEntry> skills, List<Equip> added, int careerSkills, int gmSkills,
                                int removed, int changed, int skipped, int noPreset, int available, boolean careerSkillsSkipped,
                                SuperCharacterSupplies.Plan supplies) {
    public SuperCharacterPlan(SuperCharacterPolicy.Options options,int level,Map<String,Integer> attributes,
        Map<Skill,SkillEntry> skills,List<Equip> added,int careerSkills,int gmSkills,int removed,int changed,
        int skipped,int noPreset,int available,boolean careerSkillsSkipped) {
        this(options,level,attributes,skills,added,careerSkills,gmSkills,removed,changed,skipped,noPreset,available,
            careerSkillsSkipped,SuperCharacterSupplies.Plan.empty());
    }
    public static void checkState(Character chr) {
        checkState(chr, null);
    }
    private static void checkState(Character chr, NPCConversationManager menu) {
        if(chr.gmLevel()!=6)throw new IllegalArgumentException("permission");
        var active = NPCScriptManager.getInstance().getCM(chr.getClient());
        if (menu != null && (active != menu || menu.getClient() != chr.getClient()
            || menu.getNpc() != 9900001 || !"gm_superchar".equals(menu.getScriptName())))
            throw new IllegalArgumentException("busy");
        if(!chr.isLoggedInWorld() || !chr.isAlive() || chr.isChangingMaps() || chr.getClient().isInTransition()
            || chr.getTrade()!=null || chr.getShop()!=null || chr.getPlayerShop()!=null || chr.getHiredMerchant()!=null
            || chr.getMiniGame()!=null || chr.getStorage().isOpened() || chr.getCashShop().isOpened() || (active != null && active != menu))
            throw new IllegalArgumentException("busy");
    }
    private static boolean careerNode(int id) {
        // Preserve task/mount/Maker and hidden/PQ skills; do not guess ownership for them.
        int suffix=id%10000000;
        // WZ invisible also marks ordinary fourth-job skills unlocked through skill books.
        return !GameConstants.isHiddenSkills(id) && !GameConstants.isPqSkill(id)
            && (suffix>=10000 || Set.of(1000,1001,1002).contains(suffix));
    }
    static Map<Integer,Skill> book(int job,boolean career) {
        String file=(job==0?"000":Integer.toString(job))+".img";
        Data data=DataProviderFactory.getDataProvider(WZFiles.SKILL).getData(file);
        if(data==null || data.getChildByPath("skill")==null)throw new IllegalArgumentException("resource:"+file);
        Map<Integer,Skill> result=new LinkedHashMap<>();
        for(Data node:data.getChildByPath("skill")) {
            int id=Integer.parseInt(node.getName());
            if(career && !careerNode(id))continue;
            Skill skill=SkillFactory.getSkill(id);
            if(skill==null || skill.getMaxLevel()<1 || skill.getMaxLevel()>127)throw new IllegalArgumentException("resource:"+id);
            result.put(id,skill);
        }
        return result;
    }
    public static SuperCharacterPlan build(Character chr,SuperCharacterPolicy.Options options) {
        checkState(chr);
        return buildChecked(chr, options);
    }
    static SuperCharacterPlan previewMenu(NPCConversationManager menu, String key) {
        var chr = menu.getPlayer();
        checkState(chr, menu);
        return buildChecked(chr, SuperCharacterPolicy.parse(new String[]{key, "timeless=true"}));
    }
    private static SuperCharacterPlan buildChecked(Character chr,SuperCharacterPolicy.Options options) {
        int level=Character.getMaxClassLevel(Job.getById(options.career().job()));
        Map<String,Integer> attributes=SuperCharacterPolicy.attributes(options.career(),GameConfig.getServerInt("max_ap"));
        int maximum=Character.getMaxInventorySlots();
        for(int type=1;type<=5;type++) {
            int slots=chr.getSlots(type);
            if(slots<1 || slots>maximum || !chr.canGainSlots(type,maximum-slots))throw new IllegalArgumentException("config");
        }
        Map<Skill,SkillEntry> skills=new LinkedHashMap<>(chr.getSkills());
        Map<Integer,Skill> target=new LinkedHashMap<>();
        boolean careerSkillsSkipped=false;
        try {
            for(int job:options.career().chain())target.putAll(book(job,true));
        } catch(IllegalArgumentException e) {
            if(!options.key().equals("evan") || !e.getMessage().startsWith("resource:"))throw e;
            target.clear();
            careerSkillsSkipped=true;
        }
        Map<Integer,Skill> known=new HashMap<>();
        // Only scan fixed career books, never the global String skill list.
        if(!careerSkillsSkipped)for(int job:chr.getSkills().keySet().stream().map(s->s.getId()/10000)
            .filter(j->SuperCharacterPolicy.CAREERS.values().stream().anyMatch(c->c.chain().contains(j))).distinct().toList()) known.putAll(book(job,true));
        int removed=0;
        if(!careerSkillsSkipped)for(Skill skill:List.copyOf(skills.keySet())) {
            int id=skill.getId(),job=id/10000;
            if(job==900 || job==910)continue;
            if(!target.containsKey(id) && (known.containsKey(id) || SuperCharacterPolicy.BEST.containsKey(id))) {
                skills.remove(skill);removed++;
            }
        }
        for(Skill skill:target.values())skills.put(skill,new SkillEntry((byte)skill.getMaxLevel(),skill.getMaxLevel(),-1));
        Map<Integer,Integer> gm=new LinkedHashMap<>();
        if(options.gmSkills().equals("native")) {
            for(int job:new int[]{900,910})for(Skill skill:book(job,false).values())gm.put(skill.getId(),skill.getMaxLevel());
        } else if(options.gmSkills().equals("best"))gm.putAll(SuperCharacterPolicy.BEST);
        for(var entry:gm.entrySet()) {
            Skill skill=SkillFactory.getSkill(entry.getKey());
            if(skill==null || skill.getMaxLevel()<entry.getValue())throw new IllegalArgumentException("resource:"+entry.getKey());
            // A selected career skill remains maxed even if the cross-career preset is lower.
            int value=target.containsKey(skill.getId())?skill.getMaxLevel():entry.getValue();
            skills.put(skill,new SkillEntry((byte)value,skill.getMaxLevel(),-1));
        }
        ItemInformationProvider ii=ItemInformationProvider.getInstance();
        int mask=Job.getById(options.career().job()).getJobNiche();
        mask=mask>=1&&mask<=5?1<<(mask-1):0;
        for(Item item:chr.getInventory(InventoryType.EQUIPPED).list()) {
            if(ii.isCash(item.getItemId()))continue;
            Map<String,Integer> req=ii.getEquipStats(item.getItemId());
            if(req==null || req.getOrDefault("reqLevel",0)>level
                || (mask!=0 && req.getOrDefault("reqJob",0)>0 && (req.get("reqJob")&mask)==0)
                || req.getOrDefault("reqSTR",0)>attributes.get("str") || req.getOrDefault("reqDEX",0)>attributes.get("dex")
                || req.getOrDefault("reqINT",0)>attributes.get("int") || req.getOrDefault("reqLUK",0)>attributes.get("luk"))
                throw new IllegalArgumentException("equipped:"+item.getItemId());
        }
        Set<String> present=new HashSet<>();
        Set<Short> positions=new HashSet<>();
        for(InventoryType type:new InventoryType[]{InventoryType.EQUIP,InventoryType.EQUIPPED}) {
            for(Item item:chr.getInventory(type).list()) {
                if(item instanceof Equip equip)present.add(SuperCharacterGear.signature(equip));
                if(type==InventoryType.EQUIP) {
                    if(item.getPosition()<1 || item.getPosition()>maximum || !positions.add(item.getPosition()))
                        throw new IllegalArgumentException("config");
                }
            }
        }
        int available=maximum-positions.size(),skipped=0,noPreset=0;
        List<Equip> added=new ArrayList<>();
        for(var definition:SuperCharacterGear.select(options)) {
            Map<String,Integer> stats=ii.getEquipStats(definition.id());
            if(stats==null)throw new IllegalArgumentException("resource:"+definition.id());
            if(ii.expiresOnLogout(definition.id()))continue;
            if(options.gear().equals("career") && mask!=0 && stats.getOrDefault("reqJob",0)>0 && (stats.get("reqJob")&mask)==0)continue;
            Equip clean=(Equip)ii.getEquipById(definition.id());
            boolean preset=Arrays.stream(SuperCharacterGear.perSlot(definition)).anyMatch(x->x!=0);
            if(!preset || (clean.getUpgradeSlots()==0 && definition.id()/10000!=111)){noPreset++;continue;}
            Equip enhanced=SuperCharacterGear.enhance(clean,definition);
            String signature=SuperCharacterGear.signature(enhanced);
            if(!present.add(signature)){skipped++;continue;}
            added.add(enhanced);
        }
        if(added.size()>available)throw new IllegalArgumentException("space:"+added.size()+":"+available);
        short position=1;
        for(Equip equip:added) {
            while(positions.contains(position))position++;
            if(position>maximum)throw new IllegalArgumentException("space");
            equip.setPosition(position);positions.add(position);
        }
        int changed=0;
        for(var entry:skills.entrySet()) {
            SkillEntry old=chr.getSkills().get(entry.getKey()),value=entry.getValue();
            if(old==null || old.skillLevel!=value.skillLevel || old.masterLevel!=value.masterLevel || old.expiration!=value.expiration)changed++;
        }
        return new SuperCharacterPlan(options,level,attributes,Map.copyOf(skills),List.copyOf(added),target.size(),gm.size(),removed,changed,skipped,noPreset,available,careerSkillsSkipped,SuperCharacterSupplies.plan(chr));
    }
    public List<Pair<Item,InventoryType>> originalInventory(Character chr) {
        List<Pair<Item,InventoryType>> all=new ArrayList<>();
        for(int type=-1;type<=5;type++) {
            InventoryType inventoryType=InventoryType.getByType((byte)type);
            if(inventoryType==InventoryType.UNDEFINED)continue;
            for(Item item:chr.getInventory(inventoryType).list())all.add(new Pair<>(item,inventoryType));
        }
        return all;
    }
    public List<Pair<Item,InventoryType>> inventory(Character chr) {
        List<Pair<Item,InventoryType>> all=originalInventory(chr);
        Set<Short> changedPositions=new HashSet<>();
        for(Item item:supplies.changed())changedPositions.add(item.getPosition());
        all.removeIf(pair->pair.getRight()==InventoryType.USE && changedPositions.contains(pair.getLeft().getPosition()));
        for(Equip equip:added)all.add(new Pair<>(equip,InventoryType.EQUIP));
        for(Item item:supplies.changed())all.add(new Pair<>(item,InventoryType.USE));
        return all;
    }
}
