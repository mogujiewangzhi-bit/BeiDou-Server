package org.gms.service;

import com.alibaba.fastjson2.JSON;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.controller.GiveController;
import org.springframework.web.bind.annotation.PathVariable;
import org.gms.config.GameConfig;
import org.gms.constants.game.ExpTable;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Pet;
import org.gms.exception.BizException;
import org.gms.manager.ServerManager;
import org.gms.model.dto.GiveResourceReqDTO;
import org.gms.net.server.Server;
import org.gms.property.ServiceProperty;
import org.gms.provider.DataProvider;
import org.gms.provider.DataProviderFactory;
import org.gms.server.ItemInformationProvider;
import org.gms.util.DatabaseConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.context.support.StaticMessageSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfflineGrantServiceTest {
    private boolean apply(Character chr, GiveResourceReqDTO data, Connection con) throws Exception {
        Method method = OfflineGrantService.class.getDeclaredMethod("apply", Character.class, GiveResourceReqDTO.class, Connection.class);
        method.setAccessible(true);
        return (boolean) method.invoke(new OfflineGrantService(), chr, data, con);
    }
    @BeforeAll
    static void bootstrapMessages() {
        StaticApplicationContext context = new StaticApplicationContext();
        ServiceProperty property = new ServiceProperty();
        property.setLanguage("zh-CN");
        StaticMessageSource messages = context.getStaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        context.getBeanFactory().registerSingleton("serviceProperty", property);
        context.getBeanFactory().registerSingleton("dataSource", mock(DataSource.class, RETURNS_DEEP_STUBS));
        for (String name : List.of("logSource", "exceptionSource")) {
            context.getBeanFactory().registerSingleton(name, messages);
        }
        for (Class<?> type : List.of(CharacterService.class, NameChangeService.class,
                WorldTransferService.class, AccountService.class, HpMpAlertService.class,
                InventoryService.class, NpcService.class, NxCouponService.class,
                NxCodeService.class, NewYearCardService.class, FamilyService.class,
                NoteService.class, AutobanConfigService.class)) {
            context.getBeanFactory().registerSingleton(type.getSimpleName(), mock(type));
        }
        new ServerManager().setApplicationContext(context);
        context.getBeanFactory().registerSingleton("configService", mock(ConfigService.class));
    }

    private GiveResourceReqDTO cash() {
        GiveResourceReqDTO data = new GiveResourceReqDTO();
        data.setDeliveryId(UUID.randomUUID().toString());
        data.setWorldId(0);
        data.setPlayerId(123);
        data.setType((byte) 0);
        data.setQuantity(100);
        return data;
    }

    @Test
    void offlineExperienceLevelsUpWithoutClientPacketsOrMapAccess() throws Exception {
        Constructor<Character> constructor = Character.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Character chr = spy(constructor.newInstance());
        chr.setLevel(1);
        chr.setClient(mock(Client.class));
        doReturn(200).when(chr).getMaxLevel();
        doReturn(200).when(chr).getMaxClassLevel();
        doReturn(List.of()).when(chr).recalcLocalStats();
        try (MockedStatic<GameConfig> config = mockStatic(GameConfig.class)) {
            config.when(() -> GameConfig.getServerInt("level_up_ap_gain")).thenReturn(5);
            chr.beginOfflineGrantInitialization();
            assertTrue(chr.gainOfflineGrantExp(ExpTable.getExpNeededForLevel(1)));
            assertEquals(2, chr.getLevel());
            assertEquals(5, chr.getRemainingAp());
            verify(chr.getClient(), never()).sendPacket(any());
            verify(chr, never()).getMap();
            chr.endOfflineGrantInitialization();
        }
    }

    @Test
    void historyRouteDoesNotDependOnCompilerParameterMetadata() throws Exception {
        assertEquals("cid", GiveController.class.getDeclaredMethod("rewards", int.class)
                .getParameters()[0].getAnnotation(PathVariable.class).value());
    }

    @Test
    void balancesNeverWrapOrAcceptNonpositiveRewards() {
        assertTrue(OfflineGrantService.fitsBalance(0, Integer.MAX_VALUE));
        assertFalse(OfflineGrantService.fitsBalance(Integer.MAX_VALUE, 1));
        assertFalse(OfflineGrantService.fitsBalance(1, 0));
        assertFalse(OfflineGrantService.fitsBalance(1, -1));
    }

    @Test
    void validatesSupportedResourcesAndCanonicalRequestId() {
        GiveResourceReqDTO data = cash();
        OfflineGrantService.validate(data);
        data.setQuantity(0);
        assertThrows(BizException.class, () -> OfflineGrantService.validate(data));
        data.setQuantity(1);
        data.setType((byte) 11);
        assertThrows(BizException.class, () -> OfflineGrantService.validate(data));
        data.setDeliveryId("1-1-1-1-1");
        assertThrows(BizException.class, () -> new OfflineGrantService().queueIfOffline(data, null));
    }

    @Test
    void dtoRoundTripPreservesEquipmentOverrides() {
        GiveResourceReqDTO data = cash();
        data.setType((byte) 6);
        data.setId(1002357);
        data.set_int((short) 1000);
        data.setPAtk((short) 200);
        data.setUpgradeSlot((byte) 7);
        GiveResourceReqDTO restored = JSON.parseObject(JSON.toJSONString(data), GiveResourceReqDTO.class);
        assertEquals(data.get_int(), restored.get_int());
        assertEquals(data.getPAtk(), restored.getPAtk());
        assertEquals(data.getUpgradeSlot(), restored.getUpgradeSlot());
    }

    @Test
    void existingRequestIsReturnedBeforeAnyOnlineLookup() throws Exception {
        GiveResourceReqDTO data = cash();
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(con.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(123);
        when(rs.getString(2)).thenReturn(JSON.toJSONString(data));
        try (MockedStatic<DatabaseConnection> db = mockStatic(DatabaseConnection.class);
             MockedStatic<Server> server = mockStatic(Server.class)) {
            db.when(DatabaseConnection::getConnection).thenReturn(con);
            assertTrue(new OfflineGrantService().queueIfOffline(data, null));
            server.verifyNoInteractions();
            data.setQuantity(200);
            assertThrows(BizException.class, () -> new OfflineGrantService().queueIfOffline(data, null));
            verify(ps, never()).executeUpdate();
        }
    }

    @Test
    void receiptsUseCallerTransactionAndRejectMissingPendingRow() throws Exception {
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(con.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(1, 0);
        assertThrows(SQLException.class, () -> AdminRewardReceipt.markDelivered(con, 123, List.of(1L, 2L)));
        verify(con, never()).commit();
        verify(con, never()).close();
        verify(ps, times(2)).setInt(2, 123);
    }

    @Test
    void petAndItemKeepTransactionLocalIdentity() throws Exception {
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(con.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("name")).thenReturn("pet");
        when(rs.getByte("level")).thenReturn((byte) 1);
        when(rs.getInt("fullness")).thenReturn(100);
        try (MockedStatic<DatabaseConnection> db = mockStatic(DatabaseConnection.class)) {
            Pet pet = Pet.loadFromDb(5000000, (short) 1, 456, con);
            Item item = new Item(5000000, (short) 1, (short) 1, pet);
            assertSame(pet, item.getPet());
            assertEquals(456, item.getPetId());
            pet.saveToDb(con);
            db.verifyNoInteractions();
            verify(con, never()).close();
        }
    }

    @Test
    void saveFailureRollsBackAndEndsLoginSuppression() throws Exception {
        Character chr = mock(Character.class);
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(con.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        GiveResourceReqDTO data = cash();
        data.setType((byte) 4);
        when(rs.getLong(1)).thenReturn(1L);
        when(rs.getString(2)).thenReturn(JSON.toJSONString(data));
        when(chr.gainOfflineGrantExp(100)).thenReturn(true);
        when(chr.saveCharToDB(eq(true), same(con), anyList())).thenReturn(false);
        try (MockedStatic<DatabaseConnection> db = mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenReturn(con);
            assertThrows(SQLException.class, () -> new OfflineGrantService().deliverAtLogin(chr));
            verify(con).rollback();
            verify(con, never()).commit();
            verify(chr).endOfflineGrantInitialization();
        }
    }

    @Test
    void blockedRewardIsRotatedWithoutCharacterSave() throws Exception {
        Character chr = mock(Character.class);
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(con.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        GiveResourceReqDTO data = cash();
        data.setType((byte) 4);
        when(rs.getLong(1)).thenReturn(1L);
        when(rs.getString(2)).thenReturn(JSON.toJSONString(data));
        try (MockedStatic<DatabaseConnection> db = mockStatic(DatabaseConnection.class)) {
            db.when(DatabaseConnection::getConnection).thenReturn(con);
            assertEquals(0, new OfflineGrantService().deliverAtLogin(chr));
            verify(con).prepareStatement(contains("attempted_at=CURRENT_TIMESTAMP(6)"));
            verify(con).commit();
            verify(chr, never()).saveCharToDB(anyBoolean(), any(), anyList());
            verify(chr).endOfflineGrantInitialization();
        }
    }

    @Test
    void fullInventoryNeverPartiallyAwardsButCompatibleStackCanFit() throws Exception {
        try (MockedStatic<DataProviderFactory> factory = mockStatic(DataProviderFactory.class)) {
            DataProvider provider = mock(DataProvider.class, RETURNS_DEEP_STUBS);
            factory.when(() -> DataProviderFactory.getDataProvider(any())).thenReturn(provider);
            try (MockedStatic<ItemInformationProvider> info = mockStatic(ItemInformationProvider.class)) {
                ItemInformationProvider ii = mock(ItemInformationProvider.class);
                info.when(ItemInformationProvider::getInstance).thenReturn(ii);
                when(ii.getSlotMax(any(), eq(2000000))).thenReturn((short) 100);
                Character chr = mock(Character.class);
                Inventory inv = new Inventory(chr, InventoryType.USE, (byte) 1);
                when(chr.getInventory(InventoryType.USE)).thenReturn(inv);
                Item stack = new Item(2000000, (short) 1, (short) 90);
                stack.setOwner("WAdmin");
                inv.addItemFromDB(stack);
                GiveResourceReqDTO data = cash();
                data.setType((byte) 5);
                data.setId(2000000);
                data.setQuantity(11);
                assertFalse(apply(chr, data, mock(Connection.class)));
                assertEquals(90, stack.getQuantity());
                data.setQuantity(10);
                assertTrue(apply(chr, data, mock(Connection.class)));
                assertEquals(100, stack.getQuantity());
                assertEquals(1, inv.list().size());
            }
        }
    }

    @Test
    void equipmentOverridesAreAppliedToCopyNotSharedTemplate() throws Exception {
        try (MockedStatic<DataProviderFactory> factory = mockStatic(DataProviderFactory.class)) {
            factory.when(() -> DataProviderFactory.getDataProvider(any())).thenReturn(mock(DataProvider.class, RETURNS_DEEP_STUBS));
            try (MockedStatic<ItemInformationProvider> info = mockStatic(ItemInformationProvider.class)) {
                ItemInformationProvider ii = mock(ItemInformationProvider.class);
                info.when(ItemInformationProvider::getInstance).thenReturn(ii);
                Equip template = new Equip(1002357, (short) 0);
                template.setStr((short) 5);
                when(ii.getEquipById(1002357)).thenReturn(template);
                GiveResourceReqDTO data = cash();
                data.setType((byte) 6);
                data.setId(1002357);
                data.setStr((short) 999);
                Equip awarded = OfflineGrantService.equipment(data);
                assertEquals(999, awarded.getStr());
                assertEquals(5, template.getStr());
                assertEquals(-1, awarded.getExpiration());
                Character chr = mock(Character.class);
                Inventory inv = new Inventory(chr, InventoryType.EQUIP, (byte) 1);
                when(chr.getInventory(InventoryType.EQUIP)).thenReturn(inv);
                inv.addItem(awarded);
                assertFalse(apply(chr, data, mock(Connection.class)));
                assertEquals(1, inv.list().size());
            }
        }
    }
}
