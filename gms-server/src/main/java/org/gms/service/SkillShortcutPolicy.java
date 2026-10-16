package org.gms.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SkillShortcutPolicy {
    private static final Map<Integer,String> KEYS=keys();
    private static Map<Integer,String> keys() {
        Map<Integer,String> result=new LinkedHashMap<>();
        int[] codes={16,17,18,19,20,21,22,23,24,25,30,31,32,33,34,35,36,37,38,44,45,46,47,48,49,50};
        String letters="QWERTYUIOPASDFGHJKLZXCVBNM";
        for(int i=0;i<codes.length;i++)result.put(codes[i],String.valueOf(letters.charAt(i)));
        for(int i=0;i<10;i++)result.put(i+2,String.valueOf((i+1)%10));
        for(int i=0;i<10;i++)result.put(i+59,"F"+(i+1));
        result.put(87,"F11");result.put(88,"F12");
        result.put(71,"Num7");result.put(72,"Num8");result.put(73,"Num9");result.put(75,"Num4");
        result.put(77,"Num6");result.put(79,"Num1");result.put(80,"Num2");result.put(81,"Num3");
        result.put(82,"Num0");result.put(83,"Num.");
        result.put(29,"Ctrl");result.put(42,"Shift");result.put(56,"Alt");result.put(57,"Space");
        return Map.copyOf(result);
    }
    // V83's sub-1000 skill numbers are passive or automatically triggered abilities.
    static boolean bindable(int skillId) { return skillId%10000>=1000; }
    record Plan(List<Map<String,Object>> bindings,Map<String,Object> diff) {}
    static List<Map<String,Object>> options(List<Map<String,Object>> bindings) {
        return KEYS.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry->{
            Map<String,Object> option=new LinkedHashMap<>();option.put("key",entry.getKey());option.put("label",entry.getValue());
            var current=bindings.stream().filter(row->number(row,"key")==entry.getKey()&&number(row,"type")!=0).findFirst().orElse(null);
            option.put("binding",current);return option;
        }).toList();
    }
    static int number(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    static Plan plan(int skillId,String action,String requestedMode,Number keyCode,boolean replace,List<Map<String,Object>> before) {
        String mode=requestedMode==null?"auto":requestedMode;
        if(!List.of("auto","key","none").contains(mode))throw CharacterSkillAdminService.failure("keyMode");
        if(!mode.equals("key") && (keyCode!=null || replace))throw CharacterSkillAdminService.failure("keyMode");
        List<Map<String,Object>> after=new ArrayList<>(before);
        Map<String,Object> diff=new LinkedHashMap<>();String status;
        if(action.equals("remove")) {
            after.removeIf(row->number(row,"type")==1&&number(row,"action")==skillId);status="removed";
        } else if(mode.equals("none")){status="none";}
        else if(!bindable(skillId)) {
            if(mode.equals("key"))throw CharacterSkillAdminService.failure("notBindable");
            status="notBindable";
        } else {
            Integer key=null;
            if(mode.equals("key")) {
                key=CharacterSkillAdminService.integer(keyCode,0,89);
                if(!KEYS.containsKey(key))throw CharacterSkillAdminService.failure("keyMode");
            } else {
                key=before.stream().filter(row->number(row,"type")==1&&number(row,"action")==skillId&&KEYS.containsKey(number(row,"key")))
                    .map(row->number(row,"key")).min(Integer::compareTo).orElse(null);
                if(key==null) {
                    List<Integer> preferred=new ArrayList<>(List.of(72,73,71,80,81,79,77,75,82,83));
                    preferred.addAll(KEYS.keySet().stream().sorted().toList());
                    for(int candidate:preferred) {
                        if(before.stream().noneMatch(row->number(row,"key")==candidate&&number(row,"type")!=0)){key=candidate;break;}
                    }
                }
                if(key==null)throw CharacterSkillAdminService.failure("noFreeKey");
            }
            int target=key;
            var occupied=before.stream().filter(row->number(row,"key")==target&&number(row,"type")!=0).findFirst().orElse(null);
            boolean same=occupied!=null&&number(occupied,"type")==1&&number(occupied,"action")==skillId;
            if(occupied!=null&&!same&&!replace)throw CharacterSkillAdminService.failure("keyOccupied");
            status=same?"existing":"assigned";
            after.removeIf(row->number(row,"key")==target);
            after.add(Map.of("key",target,"type",1,"action",skillId));
            diff.put("key",target);diff.put("label",KEYS.get(target));
        }
        after.sort(Comparator.comparingInt(row->number(row,"key")));
        diff.put("status",status);
        diff.put("before",before.stream().filter(row->!after.contains(row)).toList());
        diff.put("after",after.stream().filter(row->!before.contains(row)).toList());
        return new Plan(List.copyOf(after),diff);
    }
    private SkillShortcutPolicy() {}
}
