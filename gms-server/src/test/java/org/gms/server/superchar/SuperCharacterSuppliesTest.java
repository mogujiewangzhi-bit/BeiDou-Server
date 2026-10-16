package org.gms.server.superchar;

import org.gms.client.Character;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.server.ItemInformationProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SuperCharacterSuppliesTest {
    @BeforeAll static void initialize() throws Exception {
        var method=Class.forName("org.gms.service.OfflineGrantServiceTest").getDeclaredMethod("bootstrapMessages");
        method.setAccessible(true);method.invoke(null);
    }
    private Character player() {
        Character chr=mock(Character.class,RETURNS_DEEP_STUBS);
        var inventory=new Inventory(chr,InventoryType.USE,(byte)96);
        when(chr.getInventory(InventoryType.USE)).thenReturn(inventory);
        return chr;
    }
    private Item item(int id,int position,int quantity){return new Item(id,(short)position,(short)quantity);}
    private ItemInformationProvider provider(int maximum) {
        var ii=mock(ItemInformationProvider.class);
        when(ii.getName(anyInt())).thenReturn("medicine");
        when(ii.getSlotMax(any(),anyInt())).thenReturn((short)maximum);
        return ii;
    }
    private SuperCharacterSupplies.Plan plan(Character chr,int maximum) {
        var ii=provider(maximum);
        try(var items=mockStatic(ItemInformationProvider.class)){
            items.when(ItemInformationProvider::getInstance).thenReturn(ii);
            return SuperCharacterSupplies.plan(chr);
        }
    }
    @Test void emptyInventoryGetsExactTargetsInLegalStacksWithoutMutation() {
        var chr=player();var plan=plan(chr,100);
        assertEquals(500,plan.superPotionAdded());assertEquals(200,plan.allCureAdded());assertEquals(7,plan.changed().size());
        assertTrue(chr.getInventory(InventoryType.USE).list().isEmpty());
        assertEquals(7,plan.changed().stream().map(Item::getPosition).distinct().count());
        assertTrue(plan.changed().stream().allMatch(i->i.getQuantity()==100));
    }
    @Test void configuredLargerStacksAreRespected() {
        var plan=plan(player(),1000);assertEquals(2,plan.changed().size());
        assertEquals(500,plan.changed().getFirst().getQuantity());assertEquals(200,plan.changed().getLast().getQuantity());
    }
    @Test void existingSuppliesAreOnlyToppedUpAndOriginalItemsRemainUnchanged() {
        var chr=player();var potion=spy(item(2000005,1,450));var cure=item(2050004,2,200);
        chr.getInventory(InventoryType.USE).addItemFromDB(potion);chr.getInventory(InventoryType.USE).addItemFromDB(cure);
        var plan=plan(chr,1000);assertEquals(50,plan.superPotionAdded());assertEquals(0,plan.allCureAdded());assertEquals(1,plan.changed().size());
        assertEquals(450,potion.getQuantity());assertEquals(500,plan.changed().getFirst().getQuantity());
        verify(potion,never()).getCashId();
    }
    @Test void sufficientSuppliesAreNeverDuplicated() {
        var chr=player();chr.getInventory(InventoryType.USE).addItemFromDB(item(2000005,1,600));
        chr.getInventory(InventoryType.USE).addItemFromDB(item(2050004,2,300));
        assertTrue(plan(chr,1000).changed().isEmpty());
    }
    @Test void specialMetadataIsNotLostThroughStackMerging() {
        var chr=player();var potion=item(2000005,1,450);potion.setGiftFrom("gift");potion.setOwner("owner");
        chr.getInventory(InventoryType.USE).addItemFromDB(potion);
        var plan=plan(chr,1000);assertEquals(50,plan.superPotionAdded());
        assertTrue(plan.changed().stream().noneMatch(i->i.getPosition()==1));assertEquals("gift",potion.getGiftFrom());
    }
    @Test void ExpiringSuppliesDoNotReplacePermanentTopUpTargets() {
        var chr=player();var potion=item(2000005,1,900);potion.setExpiration(1);
        chr.getInventory(InventoryType.USE).addItemFromDB(potion);
        var plan=plan(chr,1000);assertEquals(500,plan.superPotionAdded());assertEquals(900,potion.getQuantity());
    }
    @Test void fullInventoryFailsWithoutChangingAnything() {
        var chr=player();for(int i=1;i<=96;i++)chr.getInventory(InventoryType.USE).addItemFromDB(item(2000004,i,1));
        assertThrows(IllegalArgumentException.class,()->plan(chr,100));
        assertEquals(96,chr.getInventory(InventoryType.USE).list().size());
    }
    @Test void fullInventoryCanTopUpCompatibleExistingStacks() {
        var chr=player();chr.getInventory(InventoryType.USE).addItemFromDB(item(2000005,1,450));
        chr.getInventory(InventoryType.USE).addItemFromDB(item(2050004,2,200));
        for(int i=3;i<=96;i++)chr.getInventory(InventoryType.USE).addItemFromDB(item(2000004,i,1));
        assertEquals(1,plan(chr,1000).changed().size());
    }
    @Test void invalidStackConfigurationIsRejected() {assertThrows(IllegalArgumentException.class,()->plan(player(),0));}
    @Test void missingMedicineResourcesAreRejected() {
        var chr=player();var ii=provider(100);when(ii.getName(2050004)).thenReturn(null);
        try(var items=mockStatic(ItemInformationProvider.class)) {
            items.when(ItemInformationProvider::getInstance).thenReturn(ii);
            assertThrows(IllegalArgumentException.class,()->SuperCharacterSupplies.plan(chr));
            assertTrue(chr.getInventory(InventoryType.USE).list().isEmpty());
        }
    }
    @Test void committedMemoryMatchesPlanAndRepeatedPlanningIsEmpty() {
        var chr=player();var plan=plan(chr,100);
        synchronized(chr){assertEquals(7,SuperCharacterSupplies.apply(chr,plan).size());}
        assertTrue(plan(chr,100).changed().isEmpty());
    }
    @Test void memoryUpdateRequiresOperationLock() {
        assertThrows(IllegalStateException.class,()->SuperCharacterSupplies.apply(player(),SuperCharacterSupplies.Plan.empty()));
    }
}
