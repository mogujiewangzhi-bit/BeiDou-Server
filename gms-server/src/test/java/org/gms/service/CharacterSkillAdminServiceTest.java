package org.gms.service;

import org.gms.client.Skill;
import org.gms.controller.CharacterSkillAdminController;
import org.gms.exception.BizException;
import org.gms.provider.Data;
import org.gms.provider.DataProvider;
import org.gms.provider.DataProviderFactory;
import org.gms.provider.DataTool;
import org.gms.provider.wz.WZFiles;
import org.gms.client.SkillFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CharacterSkillAdminServiceTest {
    @BeforeAll static void initialize(){OfflineGrantServiceTest.bootstrapMessages();}
    private Skill skill(boolean fourth){var skill=mock(Skill.class);when(skill.getMaxLevel()).thenReturn(30);when(skill.isFourthJob()).thenReturn(fourth);return skill;}
    private CharacterSkillAdminService.Edit edit(Number id,String action,Number level,Number master,boolean cross){return new CharacterSkillAdminService.Edit(1,"a".repeat(64),id,action,level,master,cross,null);}
    @Test void rejectsMalformedNumbers() {
        for(Number value:List.of(-1,0,1.5,Double.NaN,Double.POSITIVE_INFINITY,2147483648L))assertThrows(BizException.class,()->CharacterSkillAdminService.integer(value,1,Integer.MAX_VALUE));
        assertThrows(BizException.class,()->CharacterSkillAdminService.integer(null,1,30));
        assertEquals(30,CharacterSkillAdminService.integer(30,1,30));
    }
    @Test void careerCompatibilityIncludesEarlierAdvancementsButNotOtherBranches() {
        for(int id:List.of(1001001,1301000,1311000,1321000,1000))assertTrue(CharacterSkillAdminService.compatible(id,132));
        assertFalse(CharacterSkillAdminService.compatible(1121008,132));
        assertFalse(CharacterSkillAdminService.compatible(1321000,131));
        assertTrue(CharacterSkillAdminService.compatible(22101000,2218));
        assertTrue(CharacterSkillAdminService.compatible(20011000,2218));
        assertFalse(CharacterSkillAdminService.compatible(21121000,2218));
    }
    @Test void grantsKnownSkillAndPreservesExpiration() {
        var existing=Map.<String,Object>of("id",1321000,"level",1,"masterLevel",10,"expiration",123456789L);
        var after=CharacterSkillAdminService.validate(edit(1321000,"set",20,30,false),132,0,skill(true),existing);
        assertEquals(20,after.get("level"));assertEquals(30,after.get("masterLevel"));assertEquals(123456789L,after.get("expiration"));assertEquals(1,existing.get("level"));
        assertEquals(-1L,CharacterSkillAdminService.validate(edit(1321000,"set",30,30,false),132,0,skill(true),null).get("expiration"));
    }
    @Test void rejectsMissingSkillAndUnknownActions() {
        assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(1321000,"set",1,30,false),132,0,null,null));
        assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(1321000,"clear-all",1,30,false),132,0,skill(true),null));
    }
    @Test void crossCareerNeedsExplicitConsent() {
        assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(1121008,"set",1,30,false),132,0,skill(true),null));
        assertNotNull(CharacterSkillAdminService.validate(edit(1121008,"set",1,30,true),132,0,skill(true),null));
    }
    @Test void gmSkillsCannotBeGrantedToOrdinaryPlayers() {
        assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(9001000,"set",1,0,true),132,0,skill(false),null));
        assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(9101000,"set",1,0,true),132,5,skill(false),null));
        assertNotNull(CharacterSkillAdminService.validate(edit(9101000,"set",1,0,true),132,6,skill(false),null));
    }
    @Test void validatesLevelsAndMasteryRules() {
        for(int level:List.of(0,-1,31))assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(1321000,"set",level,30,false),132,0,skill(true),null));
        for(int master:List.of(0,19,31))assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(1321000,"set",20,master,false),132,0,skill(true),null));
        assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(1301000,"set",20,30,false),132,0,skill(false),null));
        assertNotNull(CharacterSkillAdminService.validate(edit(1301000,"set",20,0,false),132,0,skill(false),null));
    }
    @Test void removalAllowsUnknownLegacySkillButRequiresExistingRow() {
        assertNull(CharacterSkillAdminService.validate(edit(999999,"remove",null,null,false),132,0,null,Map.of("id",999999)));
        assertThrows(BizException.class,()->CharacterSkillAdminService.validate(edit(999999,"remove",null,null,false),132,0,null,null));
    }
    @Test void endpointsRequireAdministratorRole() {assertEquals("hasRole('ADMIN')",CharacterSkillAdminController.class.getAnnotation(PreAuthorize.class).value());}
    @Test void rejectsOnlineTargetBeforeOpeningEditTransaction() throws Exception {
        var characters=mock(CharacterAdminService.class);when(characters.detail(1)).thenReturn(Map.of("editable",false));
        var service=new CharacterSkillAdminService(characters);
        assertThrows(BizException.class,()->service.edit(edit(1321000,"set",1,30,false),"admin",true));
    }
    @Test void catalogReadsNamesOnceAndSearchesByNameOrId() throws Exception {
        var characters=mock(CharacterAdminService.class);when(characters.detail(1)).thenReturn(Map.of("values",Map.of("job",132)));
        var service=new CharacterSkillAdminService(characters);
        var ids=CharacterSkillAdminService.class.getDeclaredField("catalogIds");ids.setAccessible(true);ids.set(service,List.of(1000,1121008,1321000));
        var provider=mock(DataProvider.class);var root=mock(Data.class);var beginner=mock(Data.class);var hero=mock(Data.class);var knight=mock(Data.class);
        when(root.iterator()).thenAnswer(call->List.of(beginner,hero,knight).iterator());
        when(beginner.getName()).thenReturn("0001000");when(hero.getName()).thenReturn("1121008");when(knight.getName()).thenReturn("1321000");
        when(provider.getData("Skill.img")).thenReturn(root);
        var basicSkill=skill(false);var fourthSkill=skill(true);
        try(var factory=mockStatic(DataProviderFactory.class);var skillFactory=mockStatic(SkillFactory.class);var tool=mockStatic(DataTool.class)) {
            factory.when(()->DataProviderFactory.getDataProvider(WZFiles.STRING)).thenReturn(provider);
            tool.when(()->DataTool.getString("name",beginner,null)).thenReturn("Snail");
            tool.when(()->DataTool.getString("name",hero,null)).thenReturn("Hero skill");
            tool.when(()->DataTool.getString("name",knight,null)).thenReturn("Dark knight skill");
            skillFactory.when(()->SkillFactory.getSkill(1000)).thenReturn(basicSkill);
            skillFactory.when(()->SkillFactory.getSkill(1121008)).thenReturn(fourthSkill);
            skillFactory.when(()->SkillFactory.getSkill(1321000)).thenReturn(fourthSkill);
            assertEquals(1,((List<?>)service.catalog(1,"Dark knight",1).get("records")).size());
            assertEquals(1,((List<?>)service.catalog(1,"1121008",1).get("records")).size());
            assertEquals(1,((List<?>)service.catalog(1,"Snail",1).get("records")).size());
            assertEquals(0,((List<?>)service.catalog(1,"missing name",1).get("records")).size());
            verify(provider,times(1)).getData("Skill.img");
            skillFactory.verify(()->SkillFactory.getSkillName(anyInt()),never());
        }
    }
}
