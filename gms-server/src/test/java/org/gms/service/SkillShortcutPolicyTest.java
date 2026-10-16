package org.gms.service;

import org.gms.exception.BizException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillShortcutPolicyTest {
    @BeforeAll static void initialize(){OfflineGrantServiceTest.bootstrapMessages();}
    private Map<String,Object> binding(int key,int type,int action){return Map.of("key",key,"type",type,"action",action);}
    @Test void activeAndPassiveSkillIdsFollowV83Convention(){
        for(int id:List.of(2001002,2001005,1321000,9101003,22181002))assertTrue(SkillShortcutPolicy.bindable(id));
        for(int id:List.of(1000000,2000001,1120003,21000000))assertFalse(SkillShortcutPolicy.bindable(id));
    }
    @Test void automaticSelectionUsesOnlyAFreeKeyAndPreservesExistingBindings(){
        var before=List.of(binding(32,4,2),binding(72,1,1001004));
        var plan=SkillShortcutPolicy.plan(2001002,"set",null,null,false,before);
        assertEquals(73,plan.diff().get("key"));assertTrue(plan.bindings().containsAll(before));assertEquals(2,before.size());
        assertEquals(List.of(),plan.diff().get("before"));
    }
    @Test void existingSkillKeyIsReused(){
        var before=List.of(binding(32,1,2001002));var plan=SkillShortcutPolicy.plan(2001002,"set","auto",null,false,before);
        assertEquals(before,plan.bindings());assertEquals("existing",plan.diff().get("status"));assertEquals(32,plan.diff().get("key"));
    }
    @Test void zeroTypeBindingsAreFree(){
        var plan=SkillShortcutPolicy.plan(2001002,"set","auto",null,false,List.of(binding(72,0,0)));
        assertEquals(List.of(binding(72,1,2001002)),plan.bindings());
    }
    @Test void aFullKeyboardDoesNotOverwriteAnything(){
        var all=SkillShortcutPolicy.options(List.of()).stream().map(option->binding(((Number)option.get("key")).intValue(),4,2)).toList();
        assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2001002,"set","auto",null,false,all));
        assertEquals(all,SkillShortcutPolicy.plan(2001002,"set","none",null,false,all).bindings());
    }
    @Test void explicitReplacementRequiresConsentAndNamesTheOldBinding(){
        var before=List.of(binding(32,4,2),binding(33,1,1321000));
        assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2001002,"set","key",32,false,before));
        var plan=SkillShortcutPolicy.plan(2001002,"set","key",32,true,before);
        assertEquals(List.of(binding(32,1,2001002),before.get(1)),plan.bindings());
        assertEquals(List.of(before.get(0)),plan.diff().get("before"));
    }
    @Test void freeExplicitKeysAndExistingSkillsNeedNoReplacementConsent(){
        assertEquals(32,SkillShortcutPolicy.plan(2001002,"set","key",32,false,List.of()).diff().get("key"));
        assertEquals("existing",SkillShortcutPolicy.plan(2001002,"set","key",32,false,List.of(binding(32,1,2001002))).diff().get("status"));
    }
    @Test void passiveSkillsNeverTakeAKey(){
        var before=List.of(binding(32,4,2));var plan=SkillShortcutPolicy.plan(2000001,"set","auto",null,false,before);
        assertEquals(before,plan.bindings());assertEquals("notBindable",plan.diff().get("status"));
        assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2000001,"set","key",32,true,before));
    }
    @Test void removalClearsOnlyDirectBindingsForTheRemovedSkill(){
        var before=List.of(binding(32,1,2001002),binding(33,1,1321000),binding(34,8,0),binding(72,1,2001002));
        assertEquals(List.of(before.get(1),before.get(2)),SkillShortcutPolicy.plan(2001002,"remove",null,null,false,before).bindings());
    }
    @Test void rejectsUnsupportedKeysAndMalformedModes(){
        for(Number key:List.of(0,1,255,32.5,Double.NaN))assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2001002,"set","key",key,true,List.of()));
        assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2001002,"set","key",null,false,List.of()));
        assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2001002,"set","random",null,false,List.of()));
        assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2001002,"set","auto",32,false,List.of()));
        assertThrows(BizException.class,()->SkillShortcutPolicy.plan(2001002,"set","auto",null,true,List.of()));
    }
    @Test void keyOptionsExposeOccupationButDoNotChangeTheState(){
        var before=List.of(binding(32,4,2));var options=SkillShortcutPolicy.options(before);
        var d=options.stream().filter(option->option.get("key").equals(32)).findFirst().orElseThrow();
        assertEquals("D",d.get("label"));assertEquals(before.get(0),d.get("binding"));assertEquals(1,before.size());
    }
}
