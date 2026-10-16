package org.gms.server.superchar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed, self-only GM initialization policy. No user-supplied IDs or stat values. */
public final class SuperCharacterPolicy {
    public record Career(int job, String primary, String secondary, List<Integer> chain) {}
    public record Options(String key, Career career, String gmSkills, String gear, String quality,
                          boolean progression, boolean timeless, boolean special, boolean confirm) {}
    public static final Map<String, Career> CAREERS = Map.ofEntries(
        entry("hero",112,"str","dex",0,100,110,111,112),
        entry("paladin",122,"str","dex",0,100,120,121,122),
        entry("darkknight",132,"str","dex",0,100,130,131,132),
        entry("fp",212,"int","luk",0,200,210,211,212),
        entry("il",222,"int","luk",0,200,220,221,222),
        entry("bishop",232,"int","luk",0,200,230,231,232),
        entry("bowmaster",312,"dex","str",0,300,310,311,312),
        entry("marksman",322,"dex","str",0,300,320,321,322),
        entry("nightlord",412,"luk","dex",0,400,410,411,412),
        entry("shadower",422,"luk","dex",0,400,420,421,422),
        entry("buccaneer",512,"str","dex",0,500,510,511,512),
        entry("corsair",522,"dex","str",0,500,520,521,522),
        entry("dawnwarrior",1112,"str","dex",1000,1100,1110,1111,1112),
        entry("blazewizard",1212,"int","luk",1000,1200,1210,1211,1212),
        entry("windarcher",1312,"dex","str",1000,1300,1310,1311,1312),
        entry("nightwalker",1412,"luk","dex",1000,1400,1410,1411,1412),
        entry("thunderbreaker",1512,"str","dex",1000,1500,1510,1511,1512),
        entry("aran",2112,"str","dex",2000,2100,2110,2111,2112),
        entry("evan",2218,"int","luk",2001,2200,2210,2211,2212,2213,2214,2215,2216,2217,2218),
        entry("gm",900,"all","all"), entry("supergm",910,"all","all"));
    public static final Map<Integer,Integer> BEST = Map.ofEntries(
        Map.entry(9101003,1),Map.entry(2000000,16),Map.entry(2001002,20),Map.entry(9001002,1),
        Map.entry(9101000,1),Map.entry(1121011,5),Map.entry(5121010,30),Map.entry(9001001,1),
        Map.entry(1121008,30),Map.entry(1221009,30),Map.entry(1311001,30),Map.entry(4221007,30),
        Map.entry(5121001,30),Map.entry(3121004,30),Map.entry(3221007,30),Map.entry(4121007,30),
        Map.entry(5221004,30),Map.entry(2221006,30));
    private static Map.Entry<String,Career> entry(String key,int id,String primary,String secondary,Integer... chain) {
        return Map.entry(key,new Career(id,primary,secondary,List.of(chain)));
    }
    public static Options parse(String[] args) {
        if (args==null || args.length < 1 || args.length > 8 || args[0]==null || !CAREERS.containsKey(args[0])) throw new IllegalArgumentException("input");
        Map<String,String> values = new LinkedHashMap<>();
        boolean confirm = "confirm".equals(args[args.length-1]);
        int end = args.length - (confirm ? 1 : 0);
        for (int i=1;i<end;i++) {
            if(args[i]==null)throw new IllegalArgumentException("input");
            String[] pair=args[i].split("=",-1);
            if (pair.length!=2 || values.putIfAbsent(pair[0],pair[1])!=null) throw new IllegalArgumentException("input");
            Set<String> allowed = switch(pair[0]) {
                case "gmSkills" -> Set.of("none","native","best");
                case "gear" -> Set.of("none","career","all");
                case "quality" -> Set.of("boosted");
                case "progression","timeless","special" -> Set.of("true","false");
                default -> Set.of();
            };
            if (!allowed.contains(pair[1])) throw new IllegalArgumentException("input");
        }
        return new Options(args[0],CAREERS.get(args[0]),values.getOrDefault("gmSkills","none"),
            values.getOrDefault("gear","career"),values.getOrDefault("quality","boosted"),
            Boolean.parseBoolean(values.getOrDefault("progression","true")),
            Boolean.parseBoolean(values.getOrDefault("timeless","false")),
            Boolean.parseBoolean(values.getOrDefault("special","false")),confirm);
    }
    public static Map<String,Integer> attributes(Career career,int cap) {
        if(cap<4 || cap>Short.MAX_VALUE)throw new IllegalArgumentException("config");
        int baseline=Math.min(300,cap);
        Map<String,Integer> stats=new LinkedHashMap<>(Map.of("str",baseline,"dex",baseline,"int",baseline,"luk",baseline));
        if(career.primary().equals("all")) stats.replaceAll((k,v)->cap);
        else {stats.put(career.primary(),Math.min(5000,cap));stats.put(career.secondary(),Math.min(3000,cap));}
        return Map.copyOf(stats);
    }
    public static boolean knownCareerSkill(int id) {
        return CAREERS.values().stream().anyMatch(c -> c.chain().contains(id/10000));
    }
    private SuperCharacterPolicy() {}
}
