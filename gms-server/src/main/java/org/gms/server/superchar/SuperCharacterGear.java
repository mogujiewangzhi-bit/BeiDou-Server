package org.gms.server.superchar;

import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Item;
import org.gms.server.ItemInformationProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Equipment IDs from the handoff, with explicit scope and enhancement branches. */
public final class SuperCharacterGear {
    public record Definition(int id, String primary, boolean mage, boolean progression, boolean timeless, boolean special) {}
    private static final Map<String,List<Definition>> REGISTRY = build();
    private static Map<String,List<Definition>> build() {
        Map<String,List<Definition>> result=new LinkedHashMap<>();
        for(var entry:SuperCharacterPolicy.CAREERS.entrySet()) {
            String key=entry.getKey(), primary=entry.getValue().primary();
            if(primary.equals("all"))primary="str";
            boolean mage=primary.equals("int");
            List<Definition> list=new ArrayList<>();
            add(list,primary,mage,false,false,false,1002357,1132004,1112400,1112401,1112402,1112405);
            add(list,primary,mage,false,true,false,1032031,1102172,1122012);
            int group=switch(key) {
                case "hero","paladin","darkknight","dawnwarrior","aran" -> 0;
                case "fp","il","bishop","blazewizard","evan" -> 1;
                case "bowmaster","marksman","windarcher" -> 2;
                case "nightlord","shadower","nightwalker" -> 3;
                case "buccaneer","corsair","thunderbreaker" -> 4;
                default -> -1;
            };
            int eye=switch(group) {
                case 0 -> 1022082;
                case 1 -> 1022058;
                case 2 -> 1022060;
                case 3 -> 1022073;
                case 4 -> 1022089;
                default -> 1022089;
            };
            add(list,primary,mage,false,false,false,eye);
            if(group>=0) {
                add(list,primary,mage,false,true,false,1002776+group,1052155+group,1082234+group,1072355+group);
                add(list,primary,mage,false,false,false,1142009+group);
            }
            int[] finalWeapons=switch(key) {
                case "hero","dawnwarrior" -> new int[]{1402046};
                case "paladin" -> new int[]{1302081,1092058};
                case "darkknight" -> new int[]{1432047};
                case "aran" -> new int[]{1442063};
                case "fp","blazewizard" -> new int[]{1382045,1382057,1092057};
                case "il" -> new int[]{1382047,1382057,1092057};
                case "bishop","evan" -> new int[]{1372044,1382057,1092057};
                case "bowmaster","windarcher" -> new int[]{1452057};
                case "marksman" -> new int[]{1462050};
                case "nightlord","nightwalker" -> new int[]{1472068};
                case "shadower" -> new int[]{1332074,1332073,1092059};
                case "buccaneer","thunderbreaker" -> new int[]{1482023};
                case "corsair" -> new int[]{1492023};
                default -> new int[0];
            };
            for(int id:finalWeapons)add(list,primary,mage,false,id!=1382045&&id!=1382047,false,id);
            int[] progression=switch(key) {
                case "hero","dawnwarrior" -> new int[]{1402016,1402037,1402036,1402047};
                case "paladin" -> new int[]{1302023,1302056,1302059,1302086};
                case "darkknight" -> new int[]{1432010,1432011,1432030,1432038,1432049};
                case "aran" -> new int[]{1442020,1442044,1442045,1442067};
                case "fp","il","bishop","blazewizard","evan" -> new int[]{1372010,1372032,1372045,1382036,1382059};
                case "bowmaster","windarcher" -> new int[]{1452017,1452019,1452044,1452059};
                case "marksman" -> new int[]{1462018,1462015,1462039,1462051};
                case "nightlord","nightwalker" -> new int[]{1472033,1472053,1472051,1472071};
                case "shadower" -> new int[]{1332026,1332051,1332049,1332076};
                case "buccaneer","thunderbreaker" -> new int[]{1482011,1482012,1482013,1482024};
                case "corsair" -> new int[]{1492011,1492012,1492013,1492025};
                default -> new int[0];
            };
            add(list,primary,mage,true,false,false,progression);
            add(list,primary,mage,false,false,true,1012132,1122059,1112405,1132013);
            add(list,primary,mage,false,false,true,mage?new int[]{1032048,1082246,1102042,1142010}:new int[]{1082223,1072344,1102206,1142064});
            int[] special=switch(key) {
                case "hero","dawnwarrior" -> new int[]{1302101,1402037};
                case "paladin" -> new int[]{1302101,1422030,1422031};
                case "darkknight" -> new int[]{1432023,1432018,1432045,1432039,1432048,1432046,1432013};
                case "aran" -> new int[]{1442057,1442068,1442018,1442039,1442046,1442026,1442027,1442028,1442029,1442004,1442021,1442022,1442023};
                case "shadower" -> new int[]{1332020,1332030,1332032};
                case "fp" -> new int[]{1382045,1382046};
                case "blazewizard" -> new int[]{1382045};
                case "il" -> new int[]{1382047,1382048};
                default -> new int[0];
            };
            add(list,primary,mage,false,false,true,special);
            result.put(key,List.copyOf(list));
        }
        return Map.copyOf(result);
    }
    private static void add(List<Definition> list,String primary,boolean mage,boolean progression,boolean timeless,boolean special,int...ids) {
        for(int id:ids)list.add(new Definition(id,primary,mage,progression,timeless,special));
    }
    public static List<Definition> select(SuperCharacterPolicy.Options options) {
        if(options.gear().equals("none"))return List.of();
        Map<String,Definition> result=new LinkedHashMap<>();
        List<Definition> candidates=options.gear().equals("all")?REGISTRY.values().stream().flatMap(List::stream).toList():REGISTRY.get(options.key());
        for(var d:candidates) {
            if(d.progression()&&!options.progression() || d.timeless()&&!options.timeless() || d.special()&&!options.special())continue;
            result.putIfAbsent(d.id()+":"+d.primary()+":"+d.mage(),d);
        }
        return List.copyOf(result.values());
    }
    public static int[] stats(Equip e) {
        return new int[]{e.getStr(),e.getDex(),e.getInt(),e.getLuk(),e.getHp(),e.getMp(),e.getWatk(),e.getMatk(),e.getWdef(),e.getMdef(),e.getAcc(),e.getAvoid(),e.getSpeed(),e.getJump()};
    }
    public static String signature(Equip e) {
        return e.getItemId()+":"+Arrays.toString(stats(e))+":"+e.getUpgradeSlots()+":"+e.getLevel()+":"+e.getItemLevel()+":"+e.getItemExp()+":"+e.getVicious();
    }
    public static int[] perSlot(Definition d) {
        int[] delta=new int[14];
        int primary=switch(d.primary()){case "str"->0;case "dex"->1;case "int"->2;case "luk"->3;default->throw new IllegalArgumentException("branch");};
        int category=d.id()/10000;
        if(category>=130&&category<=149) {delta[d.mage()?7:6]=5;delta[primary]=3;}
        else switch(category) {
            case 108 -> {delta[d.mage()?7:6]=3;if(d.mage())delta[2]=3;}
            case 107 -> delta[d.mage()?2:6]=3;
            case 105 -> delta[primary]=5;
            case 100 -> delta[primary]=3;
            case 101,102 -> {delta[primary]=3;delta[10]=2;}
            case 111 -> {delta[primary]=5;delta[d.mage()?7:6]=3;delta[4]=50;delta[5]=50;}
            case 110,112,113 -> delta[primary]=3;
            case 103 -> {delta[primary]=3;if(d.mage())delta[7]=5;}
            case 109 -> {delta[d.mage()?7:6]=3;delta[d.mage()?2:0]=2;}
            default -> { }
        }
        return delta;
    }
    public static short boost(int value,int increase,int slots) {
        long upgraded=(long)value+(long)increase*slots;
        return (short)Math.min(Short.MAX_VALUE,upgraded>0?upgraded*3/2:upgraded);
    }
    public static Equip enhance(Equip clean,Definition definition) {
        Equip e=(Equip)clean.copy();int[] values=stats(e), delta=perSlot(definition);
        int slots=Byte.toUnsignedInt(e.getUpgradeSlots());
        boolean directRing=slots==0 && definition.id()/10000==111;
        if((slots==0&&!directRing) || Arrays.stream(delta).noneMatch(x->x!=0))throw new IllegalArgumentException("resource:"+definition.id());
        // Rings without scroll slots receive a GM stat bonus, not fictitious scroll history.
        int bonusUnits=directRing?10:slots;
        short[] v=new short[14];for(int i=0;i<14;i++)v[i]=boost(values[i],delta[i],bonusUnits);
        e.setStr(v[0]);e.setDex(v[1]);e.setInt(v[2]);e.setLuk(v[3]);e.setHp(v[4]);e.setMp(v[5]);
        e.setWatk(v[6]);e.setMatk(v[7]);e.setWdef(v[8]);e.setMdef(v[9]);e.setAcc(v[10]);e.setAvoid(v[11]);e.setSpeed(v[12]);e.setJump(v[13]);
        if(Arrays.stream(delta).anyMatch(x->x!=0)){e.setUpgradeSlots((byte)0);e.setLevel((byte)Math.min(127,e.getLevel()+slots));}
        return e;
    }
    private SuperCharacterGear() {}
}
