package org.gms.server.superchar;

import org.gms.client.Character;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.ModifyInventory;
import org.gms.server.ItemInformationProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Plans permanent medicine top-ups without modifying the character before commit. */
public final class SuperCharacterSupplies {
    public record Plan(List<Item> changed, int superPotionAdded, int allCureAdded) {
        public static Plan empty() { return new Plan(List.of(),0,0); }
    }
    public static Plan plan(Character chr) {
        var inventory=chr.getInventory(InventoryType.USE);
        var ii=ItemInformationProvider.getInstance();
        Set<Short> positions=new HashSet<>();
        List<Item> original=List.copyOf(inventory.list());
        for(Item item:original) {
            if(item.getPosition()<1 || item.getPosition()>Character.getMaxInventorySlots()
                || !positions.add(item.getPosition()) || item.getQuantity()<0)throw new IllegalArgumentException("config");
        }
        List<Item> changed=new ArrayList<>();
        int[] added=new int[2];
        int[][] targets={{2000005,500},{2050004,200}};
        for(int index=0;index<targets.length;index++) {
            int id=targets[index][0],target=targets[index][1];
            int maximum=ii.getSlotMax(chr.getClient(),id);
            if(ii.getName(id)==null || ii.isCash(id) || maximum<1 || maximum>Short.MAX_VALUE)throw new IllegalArgumentException("resource:"+id);
            int total=original.stream().filter(item->item.getItemId()==id && item.getExpiration()==-1)
                .mapToInt(Item::getQuantity).sum();
            int remaining=Math.max(0,target-total);
            added[index]=remaining;
            for(Item item:original) {
                if(remaining==0)break;
                if(item.getItemId()!=id || item.getExpiration()!=-1 || item.getFlag()!=0 || !item.getOwner().isEmpty()
                    || !item.getGiftFrom().isEmpty() || item.getSN()!=0 || item.getPetId()!=-1)continue;
                int increase=Math.min(remaining,Math.max(0,maximum-item.getQuantity()));
                if(increase==0)continue;
                Item copy=item.copy();copy.setQuantity((short)(item.getQuantity()+increase));
                changed.add(copy);remaining-=increase;
            }
            while(remaining>0) {
                short position=1;
                while(positions.contains(position))position++;
                if(position>Character.getMaxInventorySlots())throw new IllegalArgumentException("space:"+id);
                int quantity=Math.min(remaining,maximum);
                changed.add(new Item(id,position,(short)quantity));positions.add(position);remaining-=quantity;
            }
        }
        return new Plan(List.copyOf(changed),added[0],added[1]);
    }
    /** Called only after the same transaction has persisted both gear and supplies. */
    public static List<ModifyInventory> apply(Character chr,Plan plan) {
        if(!Thread.holdsLock(chr))throw new IllegalStateException("busy");
        var inventory=chr.getInventory(InventoryType.USE);
        List<ModifyInventory> updates=new ArrayList<>();
        for(Item item:plan.changed()) {
            Item previous=inventory.getItem(item.getPosition());
            if(previous!=null && previous.getItemId()!=item.getItemId())throw new IllegalStateException("config");
            if(previous!=null)inventory.removeSlot(item.getPosition());
            inventory.addItemFromDB(item);
            updates.add(new ModifyInventory(previous==null?0:1,item));
        }
        return updates;
    }
    private SuperCharacterSupplies() {}
}
