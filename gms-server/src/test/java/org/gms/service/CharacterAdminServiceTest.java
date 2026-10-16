package org.gms.service;

import org.gms.config.GameConfig;
import org.gms.controller.CharacterAdminController;
import org.gms.exception.BizException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CharacterAdminServiceTest {
    @BeforeAll static void initialize(){OfflineGrantServiceTest.bootstrapMessages();}
    static Map<String,Object> before() {
        Map<String,Object> values=new LinkedHashMap<>();
        for(var field:CharacterAdminService.FIELDS)values.put(field.key(),field.min());
        values.put("level",200);values.put("job",132);values.put("exp",0);
        values.put("hp",100);values.put("maxhp",100);values.put("mp",50);values.put("maxmp",50);
        values.put("sp","0,0,0,0,0,0,0,0,0,0");
        return values;
    }
    private Map<String,Object> patch(Map<String,Object> values,Map<String,Object> changes) {
        try(var config=mockStatic(GameConfig.class)) {
            config.when(()->GameConfig.getServerInt("max_ap")).thenReturn(32767);
            return CharacterAdminService.patch(values,changes);
        }
    }
    @ParameterizedTest @ValueSource(strings={"id","accountid","world","name","guildid","party","partnerId","loggedin","password","webadmin","unknown","str`=1,gm"})
    void identityAndInjectedColumnsCannotBeEdited(String key){assertThrows(BizException.class,()->patch(before(),Map.of(key,1)));}
    @Test void allNumericFieldsRejectOverflowAndFractionalValues() {
        for(var field:CharacterAdminService.FIELDS) {
            if(field.type().equals("sp"))continue;
            assertThrows(BizException.class,()->patch(before(),Map.of(field.key(),(long)field.max()+1)),field.key());
            assertThrows(BizException.class,()->patch(before(),Map.of(field.key(),(long)field.min()-1)),field.key());
            assertThrows(BizException.class,()->patch(before(),Map.of(field.key(),1.5)),field.key());
            assertThrows(BizException.class,()->patch(before(),Map.of(field.key(),"1")),field.key());
        }
    }
    @ParameterizedTest @ValueSource(strings={"1","0,0","1,2,3,4,5,6,7,8,9,32768","0,0,0,0,0,0,0,0,0,-1","a,0,0,0,0,0,0,0,0,0"})
    void rejectsMalformedSkillPoints(String sp){assertThrows(BizException.class,()->patch(before(),Map.of("sp",sp)));}
    @Test void validPatchPreservesInputAndOtherValues() {
        var before=before();var after=patch(before,Map.of("str",32767,"maxhp",30000,"hp",30000,"fame",-100,"nxCredit",10000,"sp","01,2,3,4,5,6,7,8,9,10"));
        assertEquals(30000,after.get("hp"));assertEquals(100,before.get("hp"));
        assertEquals("1,2,3,4,5,6,7,8,9,10",after.get("sp"));assertEquals(before.get("job"),after.get("job"));
    }
    @Test void validatesHpMpJobCapsAndExperience() {
        assertThrows(BizException.class,()->patch(before(),Map.of("hp",101)));
        assertThrows(BizException.class,()->patch(before(),Map.of("mp",51)));
        assertThrows(BizException.class,()->patch(before(),Map.of("job",999)));
        assertThrows(BizException.class,()->patch(before(),Map.of("job",1112)));
        assertThrows(BizException.class,()->patch(before(),Map.of("exp",1)));
        assertThrows(BizException.class,()->patch(before(),Map.of("level",1,"exp",100000)));
        assertEquals(120,patch(before(),Map.of("job",1112,"level",120)).get("level"));
    }
    @Test void emptyAndNullChangesAreRejected() {
        assertThrows(BizException.class,()->patch(before(),Map.of()));
        assertThrows(BizException.class,()->patch(before(),null));
        Map<String,Object> changes=new LinkedHashMap<>();changes.put("str",null);
        assertThrows(BizException.class,()->patch(before(),changes));
    }
    @Test void endpointsRequireAdministratorRoleAndExplicitCidBinding() {
        assertEquals("hasRole('ADMIN')",CharacterAdminController.class.getAnnotation(PreAuthorize.class).value());
    }
    @Test void databaseColumnCasingMapsToStableUiKeys() {
        assertEquals("pqpoints",CharacterAdminService.canonicalField("PQPoints"));
        assertEquals("hpMpUsed",CharacterAdminService.canonicalField("hpmpused"));
        assertEquals("familyId",CharacterAdminService.canonicalField("FamilyID"));
        assertEquals("otherColumn",CharacterAdminService.canonicalField("otherColumn"));
    }
}
