package org.gms.server.superchar;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.client.command.commands.gm6.SuperCharacterCommand;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.config.GameConfig;
import org.gms.model.pojo.SkillEntry;
import org.gms.provider.Data;
import org.gms.provider.DataProviderFactory;
import org.gms.provider.DataTool;
import org.gms.provider.wz.XMLWZFile;
import org.gms.server.ItemInformationProvider;
import org.gms.scripting.npc.NPCConversationManager;
import org.gms.scripting.npc.NPCScriptManager;
import org.gms.manager.ServerManager;
import org.gms.service.GachaponService;
import org.springframework.context.ConfigurableApplicationContext;
import javax.script.ScriptContext;
import javax.script.ScriptEngineManager;
import javax.script.Invocable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class SuperCharacterTest {
    @TempDir Path temporary;
    private static XMLWZFile wz;
    private static final Map<Integer,Data> equipment = new HashMap<>();
    private static final Map<Integer,Skill> skillCache = new HashMap<>();

    @BeforeAll static void initialize() throws Exception {
        Method method = Class.forName("org.gms.service.OfflineGrantServiceTest").getDeclaredMethod("bootstrapMessages");
        method.setAccessible(true); method.invoke(null);
        ((ConfigurableApplicationContext) ServerManager.getApplicationContext()).getBeanFactory()
            .registerSingleton("gachaponService", mock(GachaponService.class));
        Path root = Path.of(System.getProperty("superchar.resources"));
        wz = new XMLWZFile(root.resolve("Skill.wz"));
        Set<Integer> needed=SuperCharacterGear.select(SuperCharacterPolicy.parse(new String[]{"supergm","gear=all","timeless=true","special=true"}))
            .stream().map(SuperCharacterGear.Definition::id).collect(Collectors.toSet());
        try (var files = Files.walk(root.resolve("Character.wz"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".img.xml")).toList()) {
                String name = file.getFileName().toString().replace(".img.xml", "");
                if (!name.matches("0[0-9]{7}")) continue;
                int id = Integer.parseInt(name);
                if(!needed.contains(id))continue;
                equipment.put(id, new XMLWZFile(file.getParent()).getData(name + ".img"));
            }
        }
    }

    private Character player() {
        Character chr = mock(Character.class, RETURNS_DEEP_STUBS);
        when(chr.gmLevel()).thenReturn(6);
        when(chr.isLoggedInWorld()).thenReturn(true); when(chr.isAlive()).thenReturn(true);
        when(chr.getId()).thenReturn(123); when(chr.getAccountId()).thenReturn(456);
        when(chr.getJob()).thenReturn(Job.DARKKNIGHT);
        when(chr.getSkills()).thenReturn(new LinkedHashMap<>());
        for (int type = -1; type <= 5; type++) {
            InventoryType it = InventoryType.getByType((byte) type);
            if (it == InventoryType.UNDEFINED) continue;
            when(chr.getInventory(it)).thenReturn(new Inventory(chr,it,(byte)24));
        }
        for(int type=1;type<=5;type++) {
            when(chr.getSlots(type)).thenReturn((byte)(type==5?96:24));
            when(chr.canGainSlots(eq(type),anyInt())).thenReturn(true);
        }
        when(chr.getTrade()).thenReturn(null); when(chr.getShop()).thenReturn(null);
        when(chr.getPlayerShop()).thenReturn(null); when(chr.getHiredMerchant()).thenReturn(null);
        when(chr.getMiniGame()).thenReturn(null);
        when(chr.getRemainingSps()).thenReturn(new int[10]);
        return chr;
    }

    private ItemInformationProvider items() {
        ItemInformationProvider ii = mock(ItemInformationProvider.class);
        when(ii.getName(2000005)).thenReturn("Power Elixir");
        when(ii.getName(2050004)).thenReturn("All Cure Potion");
        when(ii.getSlotMax(any(),eq(2000005))).thenReturn((short)100);
        when(ii.getSlotMax(any(),eq(2050004))).thenReturn((short)100);
        when(ii.getEquipStats(anyInt())).thenAnswer(call -> {
            Data data=equipment.get(call.getArgument(0));
            if(data==null)return null;
            Map<String,Integer> stats=new HashMap<>();
            for(Data node:data.getChildByPath("info")) {
                if(node.getName().startsWith("req"))stats.put(node.getName(),DataTool.getIntConvert(node));
            }
            return stats;
        });
        when(ii.expiresOnLogout(anyInt())).thenAnswer(call -> DataTool.getIntConvert("info/expireOnLogout",equipment.get(call.getArgument(0)),0)==1);
        when(ii.getEquipById(anyInt())).thenAnswer(call -> template(call.getArgument(0)));
        return ii;
    }

    private Equip template(int id) {
        Data data=equipment.get(id);
        if(data==null)throw new IllegalArgumentException("resource:"+id);
        Equip e=new Equip(id,(short)0);
        e.setStr((short)DataTool.getIntConvert("info/incSTR",data,0)); e.setDex((short)DataTool.getIntConvert("info/incDEX",data,0));
        e.setInt((short)DataTool.getIntConvert("info/incINT",data,0)); e.setLuk((short)DataTool.getIntConvert("info/incLUK",data,0));
        e.setWatk((short)DataTool.getIntConvert("info/incPAD",data,0)); e.setMatk((short)DataTool.getIntConvert("info/incMAD",data,0));
        e.setHp((short)DataTool.getIntConvert("info/incMHP",data,0)); e.setMp((short)DataTool.getIntConvert("info/incMMP",data,0));
        e.setWdef((short)DataTool.getIntConvert("info/incPDD",data,0)); e.setMdef((short)DataTool.getIntConvert("info/incMDD",data,0));
        e.setAcc((short)DataTool.getIntConvert("info/incACC",data,0)); e.setAvoid((short)DataTool.getIntConvert("info/incEVA",data,0));
        e.setSpeed((short)DataTool.getIntConvert("info/incSpeed",data,0)); e.setJump((short)DataTool.getIntConvert("info/incJump",data,0));
        e.setUpgradeSlots((byte)DataTool.getIntConvert("info/tuc",data,0));
        return e;
    }

    private Skill skill(int id) {
        if(skillCache.containsKey(id))return skillCache.get(id);
        Data data=wz.getData((id/10000==0?"000":id/10000)+".img");
        Data node=null;
        if(data!=null && data.getChildByPath("skill")!=null) {
            for(Data entry:data.getChildByPath("skill")) {
                if(Integer.parseInt(entry.getName())==id){node=entry.getChildByPath("level");break;}
            }
        }
        if(node==null || node.getChildren().isEmpty())return null;
        Skill skill=mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getMaxLevel()).thenReturn(node.getChildren().size());
        skillCache.put(id,skill);
        return skill;
    }

    private SuperCharacterPlan build(Character chr,String... args) {
        return buildPlan(chr, null, args);
    }
    private SuperCharacterPlan buildPlan(Character chr,NPCConversationManager menu,String... args) {
        try(MockedStatic<DataProviderFactory> data=mockStatic(DataProviderFactory.class);
            MockedStatic<SkillFactory> skills=mockStatic(SkillFactory.class);
            MockedStatic<GameConfig> config=mockStatic(GameConfig.class);
            MockedStatic<ItemInformationProvider> items=mockStatic(ItemInformationProvider.class)) {
            data.when(()->DataProviderFactory.getDataProvider(any())).thenReturn(wz);
            skills.when(()->SkillFactory.getSkill(anyInt())).thenAnswer(call->skill(call.getArgument(0)));
            config.when(()->GameConfig.getServerInt("max_ap")).thenReturn(32767);
            ItemInformationProvider provider=items();
            items.when(ItemInformationProvider::getInstance).thenReturn(provider);
            return menu == null ? SuperCharacterPlan.build(chr,SuperCharacterPolicy.parse(args)) : SuperCharacterPlan.previewMenu(menu,args.length == 0 ? "darkknight" : args[0]);
        }
    }

    @ParameterizedTest @ValueSource(strings={"hero","paladin","darkknight","fp","il","bishop","bowmaster","marksman","nightlord","shadower","buccaneer","corsair","dawnwarrior","blazewizard","windarcher","nightwalker","thunderbreaker","aran","evan","gm","supergm"})
    void menuPreviewIsScopedReadOnlyAndUsesTimelessPreset(String key) {
        Character chr = player();
        Client client = chr.getClient();
        NPCConversationManager menu = mock(NPCConversationManager.class);
        when(menu.getPlayer()).thenReturn(chr); when(menu.getClient()).thenReturn(client);
        when(menu.getNpc()).thenReturn(9900001); when(menu.getScriptName()).thenReturn("gm_superchar");
        NPCScriptManager manager = mock(NPCScriptManager.class);
        when(manager.getCM(chr.getClient())).thenReturn(menu);
        try (MockedStatic<NPCScriptManager> scripts = mockStatic(NPCScriptManager.class)) {
            scripts.when(NPCScriptManager::getInstance).thenReturn(manager);
            assertThrows(IllegalArgumentException.class, () -> build(chr,"darkknight"));
            var plan = buildPlan(chr,menu,key);
            assertEquals(key,plan.options().key()); assertTrue(plan.options().timeless());
            assertFalse(plan.options().confirm()); assertFalse(plan.added().isEmpty());
            verify(chr,never()).applyCommittedSuperCharacter(any()); verify(chr,never()).saveCharToDB();
            when(menu.getScriptName()).thenReturn("other_menu");
            assertThrows(IllegalArgumentException.class, () -> buildPlan(chr,menu));
            when(menu.getScriptName()).thenReturn("gm_superchar");
            when(manager.getCM(chr.getClient())).thenReturn(null);
            assertThrows(IllegalArgumentException.class, () -> buildPlan(chr,menu));
            assertThrows(IllegalArgumentException.class, () -> SuperCharacterMenu.confirm(menu,"darkknight"));
            verify(menu,never()).dispose();
        }
    }

    @Test void menuCannotBypassPermissionsOrUnsafeState() {
        Character chr = player();
        Client client = chr.getClient();
        NPCConversationManager menu = mock(NPCConversationManager.class);
        when(menu.getPlayer()).thenReturn(chr); when(menu.getClient()).thenReturn(client);
        when(menu.getNpc()).thenReturn(9900001); when(menu.getScriptName()).thenReturn("gm_superchar");
        when(chr.getClient().getPlayer()).thenReturn(chr);
        NPCScriptManager manager = mock(NPCScriptManager.class);
        when(manager.getCM(chr.getClient())).thenReturn(menu);
        try (MockedStatic<NPCScriptManager> scripts = mockStatic(NPCScriptManager.class)) {
            scripts.when(NPCScriptManager::getInstance).thenReturn(manager);
            when(chr.gmLevel()).thenReturn(5);
            assertThrows(IllegalArgumentException.class, () -> buildPlan(chr,menu));
            assertThrows(IllegalArgumentException.class, () -> SuperCharacterMenu.confirm(menu,"darkknight"));
            verify(menu,never()).dispose();
            when(chr.gmLevel()).thenReturn(6); when(chr.getCashShop().isOpened()).thenReturn(true);
            assertThrows(IllegalArgumentException.class, () -> buildPlan(chr,menu));
            when(chr.getCashShop().isOpened()).thenReturn(false); when(chr.isChangingMaps()).thenReturn(true);
            assertThrows(IllegalArgumentException.class, () -> buildPlan(chr,menu));
        }
    }

    @Test void menuClosesConversationBeforeExecutingExactCommand() {
        Character chr = player();
        Client client = chr.getClient();
        NPCConversationManager menu = mock(NPCConversationManager.class);
        when(menu.getClient()).thenReturn(client); when(client.getPlayer()).thenReturn(chr);
        when(menu.getNpc()).thenReturn(9900001); when(menu.getScriptName()).thenReturn("gm_superchar");
        NPCScriptManager manager = mock(NPCScriptManager.class);
        when(manager.getCM(chr.getClient())).thenReturn(menu);
        try (MockedStatic<NPCScriptManager> scripts = mockStatic(NPCScriptManager.class);
             var commands = mockConstruction(SuperCharacterCommand.class, (command, context) -> {
                 doAnswer(call -> { verify(menu).dispose(); return null; }).when(command).execute(any(),any());
             })) {
            scripts.when(NPCScriptManager::getInstance).thenReturn(manager);
            assertThrows(IllegalArgumentException.class, () -> SuperCharacterMenu.confirm(menu,"bad"));
            verify(menu,never()).dispose();
            SuperCharacterMenu.confirm(menu,"darkknight");
            verify(commands.constructed().getFirst()).execute(eq(chr.getClient()),
                aryEq(new String[]{"darkknight","timeless=true","confirm"}));
        }
    }

    @Test void actualGraalEngineRunsMenuConfirmationAndPermissionPaths() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("superchar.resources")).getParent().resolve("gm_superchar.js"));
        for (int level : new int[]{5,6}) {
            Character chr = player(); when(chr.gmLevel()).thenReturn(level);
            NPCConversationManager menu = mock(NPCConversationManager.class); when(menu.getPlayer()).thenReturn(chr);
            try (MockedStatic<SuperCharacterMenu> bridge = mockStatic(SuperCharacterMenu.class)) {
                bridge.when(() -> SuperCharacterMenu.preview(menu,"darkknight")).thenReturn("preview");
                var engine = new ScriptEngineManager().getEngineByName("graal.js");
                var bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put("polyglot.js.allowHostAccess",true);
                bindings.put("polyglot.js.allowHostClassLookup",true);
                engine.put("cm",menu); engine.eval(source);
                var invocable = (Invocable)engine;
                invocable.invokeFunction("start");
                invocable.invokeFunction("action",1,4,2);
                invocable.invokeFunction("action",1,1,0);
                invocable.invokeFunction("action",1,1,0);
                bridge.verify(() -> SuperCharacterMenu.confirm(menu,"darkknight"), times(level == 6 ? 1 : 0));
                if(level == 6) verify(menu).sendYesNo("preview");
                else verify(menu).sendOk(anyString());
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"hero","paladin","darkknight","fp","il","bishop","bowmaster","marksman","nightlord","shadower","buccaneer","corsair","dawnwarrior","blazewizard","windarcher","nightwalker","thunderbreaker","aran","evan","gm","supergm"})
    void everyAvailableCareerHasValidResourcesAndIdempotentGear(String key) {
        Character chr=player();
        SuperCharacterPlan first=build(chr,key);
        assertEquals(Character.getMaxClassLevel(Job.getById(first.options().career().job())),first.level());
        assertEquals(key.contains("warrior")&&key.equals("dawnwarrior")?120:Character.getMaxClassLevel(Job.getById(first.options().career().job())),first.level());
        assertTrue(first.added().size()<=96);
        assertEquals(4,first.added().stream().filter(e->e.getItemId()/10000==111).count());
        assertEquals(4,first.added().stream().filter(e->e.getItemId()/10000==111).map(Equip::getItemId).distinct().count());
        for(Equip equip:first.added()) {
            assertEquals(0,equip.getUpgradeSlots());
            if(template(equip.getItemId()).getUpgradeSlots()>0)assertTrue(equip.getLevel()>0);
            else {assertEquals(111,equip.getItemId()/10000);assertEquals(0,equip.getLevel());}
            assertNotEquals(SuperCharacterGear.signature(template(equip.getItemId())),SuperCharacterGear.signature(equip));
        }
        for(Equip e:first.added())chr.getInventory(InventoryType.EQUIP).addItemFromDB(e);
        when(chr.getSkills()).thenReturn(first.skills());
        SuperCharacterPlan repeat=build(chr,key);
        assertEquals(0,repeat.added().size()); assertEquals(0,repeat.changed());
        verify(chr,never()).applyCommittedSuperCharacter(any());
        verify(chr,never()).saveCharToDB();
    }

    @ParameterizedTest @ValueSource(strings={"132","bad","darkknight core=true","darkknight gmSkills=core","darkknight gear=all gear=none","darkknight confirm gear=none","darkknight target=123","darkknight timeless=TRUE","darkknight password=foo"})
    void rejectsUntrustedParameters(String input) {
        assertThrows(IllegalArgumentException.class,()->SuperCharacterPolicy.parse(input.split(" ")));
    }

    @Test void defaultsAndCapsAreAbsolute() {
        var options=SuperCharacterPolicy.parse(new String[]{"darkknight"});
        assertFalse(options.confirm()); assertTrue(options.progression()); assertFalse(options.timeless()); assertFalse(options.special());
        assertEquals("boosted",options.quality()); assertEquals("none",options.gmSkills());
        assertThrows(IllegalArgumentException.class,()->SuperCharacterPolicy.parse(new String[]{"darkknight","quality=clean"}));
        assertThrows(IllegalArgumentException.class,()->SuperCharacterPolicy.parse(new String[]{"darkknight","quality=both"}));
        assertEquals(Map.of("str",5000,"dex",3000,"int",300,"luk",300),SuperCharacterPolicy.attributes(options.career(),32767));
        for(String key:List.of("gm","supergm")) {
            assertEquals(Map.of("str",32767,"dex",32767,"int",32767,"luk",32767),
                SuperCharacterPolicy.attributes(SuperCharacterPolicy.CAREERS.get(key),32767));
        }
        assertEquals(Map.of("str",2000,"dex",2000,"int",300,"luk",300),SuperCharacterPolicy.attributes(options.career(),2000));
        assertThrows(IllegalArgumentException.class,()->SuperCharacterPolicy.attributes(options.career(),32768));
        assertThrows(IllegalArgumentException.class,()->SuperCharacterPolicy.attributes(options.career(),3));
        assertThrows(IllegalArgumentException.class,()->SuperCharacterPolicy.parse(null));
    }
    @Test void supplyPreviewAndBackupKeepOriginalInventoryUntouched() {
        var chr=player();var potion=new Item(2000005,(short)1,(short)40);
        chr.getInventory(InventoryType.USE).addItemFromDB(potion);
        var plan=build(chr,"darkknight");
        assertEquals(40,potion.getQuantity());assertEquals(460,plan.supplies().superPotionAdded());
        assertEquals(1,plan.originalInventory(chr).stream().filter(p->p.getRight()==InventoryType.USE).count());
        assertEquals(500,plan.inventory(chr).stream().filter(p->p.getLeft().getItemId()==2000005).mapToInt(p->p.getLeft().getQuantity()).sum());
        assertEquals(200,plan.inventory(chr).stream().filter(p->p.getLeft().getItemId()==2050004).mapToInt(p->p.getLeft().getQuantity()).sum());
        assertEquals(40,potion.getQuantity());
    }

    @Test void allFlagCombinationsUseIndependentFilters() {
        for(boolean progression:new boolean[]{false,true})for(boolean timeless:new boolean[]{false,true})for(boolean special:new boolean[]{false,true}) {
            var options=SuperCharacterPolicy.parse(new String[]{"darkknight","progression="+progression,"timeless="+timeless,"special="+special});
            for(var d:SuperCharacterGear.select(options)) {
                assertTrue(progression||!d.progression()); assertTrue(timeless||!d.timeless()); assertTrue(special||!d.special());
            }
        }
        assertTrue(SuperCharacterGear.select(SuperCharacterPolicy.parse(new String[]{"darkknight","gear=none"})).isEmpty());
    }

    @Test void reinforcementUsesFixedSlotsAndDoesNotMutateTemplate() {
        Equip clean=template(1432049); String original=SuperCharacterGear.signature(clean);
        var d=new SuperCharacterGear.Definition(1432049,"str",false,true,false,false);
        Equip boosted=SuperCharacterGear.enhance(clean,d);
        assertEquals(original,SuperCharacterGear.signature(clean));
        assertEquals((clean.getWatk()+5*clean.getUpgradeSlots())*3/2,boosted.getWatk());
        assertEquals(0,boosted.getUpgradeSlots()); assertEquals(clean.getUpgradeSlots(),boosted.getLevel());
        assertNotEquals(original,SuperCharacterGear.signature(boosted));
        assertEquals(32767,SuperCharacterGear.boost(32760,5,7));
        assertEquals(-10,SuperCharacterGear.boost(-10,0,7)); assertEquals(0,SuperCharacterGear.boost(0,0,7));
        Equip noPreset=template(1022082);
        Equip enhanced=SuperCharacterGear.enhance(noPreset,new SuperCharacterGear.Definition(1022082,"str",false,false,false,false));
        assertEquals(0,enhanced.getUpgradeSlots());
        assertEquals(noPreset.getUpgradeSlots(),enhanced.getLevel());
        assertTrue(enhanced.getStr()>noPreset.getStr());
    }

    @ParameterizedTest @ValueSource(strings={"darkknight","bishop","marksman","nightlord","corsair"})
    void timelessIncludesCareerHatAndZakumHelmet(String career) {
        int id=Map.of("darkknight",1002776,"bishop",1002777,"marksman",1002778,
            "nightlord",1002779,"corsair",1002780).get(career);
        var hats=build(player(),career,"timeless=true").added().stream()
            .filter(e->e.getItemId()/10000==100).toList();
        assertEquals(Set.of(1002357,id),hats.stream().map(Equip::getItemId).collect(Collectors.toSet()));
        assertEquals(2,hats.size());
        for(Equip hat:hats) {
            assertEquals(0,hat.getUpgradeSlots());
            assertEquals(template(hat.getItemId()).getUpgradeSlots(),hat.getLevel());
            assertNotEquals(SuperCharacterGear.signature(template(hat.getItemId())),SuperCharacterGear.signature(hat));
        }
    }

    @Test void timelessHatFlagDoesNotRemoveZakumHelmet() {
        var hats=build(player(),"darkknight","timeless=false").added().stream()
            .filter(e->e.getItemId()/10000==100).map(Equip::getItemId).toList();
        assertEquals(List.of(1002357),hats);
    }

    @Test void timelessAndZakumHatsDeduplicateAcrossBagAndWornInventory() {
        var chr=player();
        var hats=build(chr,"darkknight","timeless=true").added().stream()
            .filter(e->e.getItemId()/10000==100).toList();
        Equip worn=(Equip)hats.get(0).copy();worn.setPosition((short)-1);
        Equip bag=(Equip)hats.get(1).copy();bag.setPosition((short)1);
        chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(worn);
        chr.getInventory(InventoryType.EQUIP).addItemFromDB(bag);
        assertEquals(0,build(chr,"darkknight","timeless=true").added().stream()
            .filter(e->e.getItemId()/10000==100).count());
        assertEquals(1,chr.getInventory(InventoryType.EQUIPPED).list().size());
        assertEquals(1,chr.getInventory(InventoryType.EQUIP).list().size());
    }

    @Test void careerEyesHaveDistinctTemplatesAndCareerStats() {
        Map<String,Integer> eyes=Map.of("darkknight",1022082,"bishop",1022058,"bowmaster",1022060,
            "nightlord",1022073,"corsair",1022089);
        for(var entry:eyes.entrySet()) {
            var plan=build(player(),entry.getKey());
            Equip eye=plan.added().stream().filter(e->e.getItemId()/10000==102).findFirst().orElseThrow();
            assertEquals(entry.getValue().intValue(),eye.getItemId());
            int index=switch(plan.options().career().primary()){case "str"->0;case "dex"->1;case "int"->2;case "luk"->3;default->throw new AssertionError();};
            assertTrue(SuperCharacterGear.stats(eye)[index]>SuperCharacterGear.stats(template(eye.getItemId()))[index]);
            assertEquals(0,eye.getUpgradeSlots());assertTrue(eye.getLevel()>0);
        }
    }

    @Test void fourRingsTopUpAcrossInventoryAndWornSlotsWithoutRemovingExcess() {
        Character chr=player();var first=build(chr,"darkknight");
        var rings=first.added().stream().filter(e->e.getItemId()/10000==111).toList();
        Equip worn=(Equip)rings.get(0).copy();worn.setPosition((short)-12);
        Equip bag=(Equip)rings.get(1).copy();bag.setPosition((short)1);
        chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(worn);
        chr.getInventory(InventoryType.EQUIP).addItemFromDB(bag);
        assertEquals(2,build(chr,"darkknight").added().stream().filter(e->e.getItemId()/10000==111).count());
        for(short position=2;position<=3;position++) {
            Equip extra=(Equip)rings.get(position).copy();extra.setPosition(position);
            chr.getInventory(InventoryType.EQUIP).addItemFromDB(extra);
        }
        Equip extra=(Equip)rings.get(0).copy();extra.setPosition((short)4);
        chr.getInventory(InventoryType.EQUIP).addItemFromDB(extra);
        assertEquals(0,build(chr,"darkknight").added().stream().filter(e->e.getItemId()/10000==111).count());
        assertEquals(4,chr.getInventory(InventoryType.EQUIP).list().size());
        assertEquals(1,chr.getInventory(InventoryType.EQUIPPED).list().size());
    }

    @Test void unscrollableGearIsSkippedRatherThanPretendingToHaveBeenScrolled() {
        var plan=build(player(),"darkknight","special=true","timeless=true");
        assertFalse(plan.added().stream().anyMatch(e->e.getItemId()==1142009));
        assertTrue(plan.noPreset()>0);
        var definition=new SuperCharacterGear.Definition(1142009,"str",false,false,false,false);
        assertThrows(IllegalArgumentException.class,()->SuperCharacterGear.enhance(template(1142009),definition));
        Equip ring=plan.added().stream().filter(e->e.getItemId()==1112405).findFirst().orElseThrow();
        assertEquals(0,ring.getLevel());assertEquals(0,ring.getUpgradeSlots());
        assertTrue(ring.getStr()>template(1112405).getStr());assertTrue(ring.getWatk()>template(1112405).getWatk());
    }

    @Test void missingEvanBookPreservesAllExistingSkillsButPlansOtherChanges() {
        Character chr=player();
        Skill existing=skill(1121008);
        Map<Skill,SkillEntry> original=Map.of(existing,new SkillEntry((byte)1,30,-1));
        when(chr.getSkills()).thenReturn(original);
        var plan=build(chr,"evan");
        assertTrue(plan.careerSkillsSkipped()); assertEquals(original,plan.skills());
        assertEquals(0,plan.changed()); assertEquals(0,plan.removed()); assertEquals(200,plan.level());
        assertEquals(5000,plan.attributes().get("int")); assertFalse(plan.added().isEmpty());
        verify(chr,never()).applyCommittedSuperCharacter(any());
    }

    @Test void sameMarksmanCareerKeepsAndMaxesInvisibleFourthJobSkills() {
        Character chr=player();
        when(chr.getJob()).thenReturn(Job.MARKSMAN);
        Map<Skill,SkillEntry> original=new LinkedHashMap<>();
        for(int id:List.of(3221000,3221001,3221003,3221005,3221007,3221008)) {
            Skill skill=skill(id);
            original.put(skill,new SkillEntry((byte)(id==3221003?28:1),skill.getMaxLevel(),-1));
        }
        Skill blessing=skill(12);
        original.put(blessing,new SkillEntry((byte)20,20,-1));
        when(chr.getSkills()).thenReturn(original);
        var plan=build(chr,"marksman","timeless=true");
        assertEquals(0,plan.removed());
        for(Skill skill:original.keySet()) {
            assertTrue(plan.skills().containsKey(skill));
            if(skill.getId()!=12)assertEquals(skill.getMaxLevel(),plan.skills().get(skill).skillLevel);
        }
        assertEquals(original.get(blessing),plan.skills().get(blessing));
        assertEquals(31,plan.careerSkills());
        verify(chr,never()).applyCommittedSuperCharacter(any());
        verify(chr,never()).saveCharToDB();
    }

    @Test void nativeAndBestSkillsUseOnlyPresentResources() {
        var nativePlan=build(player(),"darkknight","gmSkills=native","gear=none");
        assertTrue(nativePlan.gmSkills()>0);
        assertEquals(18,SuperCharacterPolicy.BEST.size());
        var best=build(player(),"darkknight","gmSkills=best","gear=none");
        assertEquals(18,best.gmSkills());
        assertFalse(best.skills().keySet().stream().anyMatch(s->s.getId()==9101006 || s.getId()==9101009));
    }

    @Test void fullInventoryRefusesWithoutExpandingOrApplying() {
        Character chr=player();
        for(short pos=1;pos<=96;pos++)chr.getInventory(InventoryType.EQUIP).addItemFromDB(new Equip(1000000,pos));
        assertThrows(IllegalArgumentException.class,()->build(chr,"darkknight"));
        assertEquals(24,chr.getInventory(InventoryType.EQUIP).getSlotLimit());
        assertEquals(96,chr.getInventory(InventoryType.EQUIP).list().size());
        verify(chr,never()).applyCommittedSuperCharacter(any());
    }

    @Test void gearNoneStillPlansMaxCapacityAndZeroEquipment() {
        assertTrue(build(player(),"bishop","gear=none").added().isEmpty());
        assertEquals(96,Character.getMaxInventorySlots());
    }

    @Test void incompatibleWornGearRefuses() {
        Character chr=player(); chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(template(1432047));
        assertThrows(IllegalArgumentException.class,()->build(chr,"bishop"));
    }

    @Test void stateAndPermissionFailClosed() {
        for(int gm:new int[]{0,1,2,3,4,5,7}) {
            Character chr=player();when(chr.gmLevel()).thenReturn(gm);
            assertThrows(IllegalArgumentException.class,()->build(chr,"darkknight"));
        }
        Character chr=player(); when(chr.getStorage().isOpened()).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->build(chr,"darkknight"));
        when(chr.getStorage().isOpened()).thenReturn(false); when(chr.isChangingMaps()).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->build(chr,"darkknight"));
    }

    @Test void oldCareerRemovedWhileUnknownAndGmPreserved() {
        Character chr=player(); Skill old=skill(1121008), gm=skill(9101003), unknown=mock(Skill.class);
        when(unknown.getId()).thenReturn(90000001);
        when(chr.getSkills()).thenReturn(Map.of(old,new SkillEntry((byte)1,30,-1),gm,new SkillEntry((byte)1,1,-1),unknown,new SkillEntry((byte)1,1,-1)));
        var plan=build(chr,"bishop","gear=none");
        assertFalse(plan.skills().containsKey(old)); assertTrue(plan.skills().containsKey(gm)); assertTrue(plan.skills().containsKey(unknown));
        assertEquals(1,plan.removed());
    }

    private Connection transaction() throws Exception {
        Connection con=mock(Connection.class);
        when(con.prepareStatement(anyString())).thenAnswer(call->{
            PreparedStatement ps=mock(PreparedStatement.class);
            when(ps.executeUpdate()).thenReturn(1);
            ResultSet rs=mock(ResultSet.class,RETURNS_DEEP_STUBS);
            if(call.<String>getArgument(0).startsWith("SELECT accountid, gm")) {
                when(rs.next()).thenReturn(true,false); when(rs.getInt(1)).thenReturn(456); when(rs.getInt(2)).thenReturn(6);
            }
            when(ps.executeQuery()).thenReturn(rs); return ps;
        });
        return con;
    }

    private SuperCharacterPlan emptyPlan() {
        var options=SuperCharacterPolicy.parse(new String[]{"darkknight","gear=none","confirm"});
        return new SuperCharacterPlan(options,200,SuperCharacterPolicy.attributes(options.career(),32767),Map.of(),List.of(),0,0,0,0,0,0,96,false);
    }

    @Test void backupFailureRollsBackWithoutApplyingMemory() throws Exception {
        Connection con=transaction(); Character chr=player();
        Path file=temporary.resolve("not-a-directory"); Files.writeString(file,"occupied");
        synchronized(chr) { assertThrows(IllegalStateException.class,()->new SuperCharacterStore(()->con,file).execute(chr,emptyPlan())); }
        verify(con).rollback(); verify(con,never()).commit(); verify(chr,never()).applyCommittedSuperCharacter(any());
    }

    @Test void skillSaveFailureRollsBackWithoutApplyingMemory() throws Exception {
        Connection con=transaction(); Character chr=player();
        when(con.prepareStatement("DELETE FROM skills WHERE characterid=?")).thenThrow(new SQLException("injected"));
        synchronized(chr) { assertThrows(IllegalStateException.class,()->new SuperCharacterStore(()->con,temporary).execute(chr,emptyPlan())); }
        verify(con).rollback(); verify(con,never()).commit(); verify(chr,never()).applyCommittedSuperCharacter(any());
    }

    @Test void commitFailureWithNoAuditDoesNotApplyMemory() throws Exception {
        Connection con=transaction(); Character chr=player(); doThrow(new SQLException("injected")).when(con).commit();
        synchronized(chr) { assertThrows(IllegalStateException.class,()->new SuperCharacterStore(()->con,temporary).execute(chr,emptyPlan())); }
        verify(con).rollback(); verify(chr,never()).applyCommittedSuperCharacter(any());
    }

    @Test void committedSyncFailureRequestsRelogAndDoesNotRepeatTransaction() throws Exception {
        Connection con=transaction(); Character chr=player(); doThrow(new RuntimeException("packet")).when(chr).applyCommittedSuperCharacter(any());
        SuperCharacterStore.Result result;
        synchronized(chr) { result=new SuperCharacterStore(()->con,temporary).execute(chr,emptyPlan()); }
        assertTrue(result.reload()); verify(con,times(1)).commit(); verify(chr).requireSuperCharacterReload();
        JSONObject backup=JSON.parseObject(Files.readString(temporary.resolve(result.backupId()+".json")));
        assertEquals(123,backup.getIntValue("characterId")); assertTrue(backup.containsKey("parameters"));
    }

    @Test void restoreRejectsMovedOrAlteredItems() {
        JSONObject after=JSON.parseObject("{\"items\":[{\"inventoryitemid\":100,\"position\":1,\"quantity\":1}],\"skills\":[]}");
        assertTrue(SuperCharacterRestore.unchanged(after,after));
        JSONObject moved=JSON.parseObject(after.toJSONString()); moved.getJSONArray("items").getJSONObject(0).put("position",2);
        assertFalse(SuperCharacterRestore.unchanged(moved,after));
        moved=JSON.parseObject(after.toJSONString()); moved.getJSONArray("items").getJSONObject(0).put("inventoryitemid",101);
        assertFalse(SuperCharacterRestore.unchanged(moved,after));
    }
}
