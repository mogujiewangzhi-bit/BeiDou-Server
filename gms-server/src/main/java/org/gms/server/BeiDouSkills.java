package org.gms.server;

import org.gms.client.Skill;
import org.gms.constants.skills.SuperGM;

import java.util.List;
import java.util.function.IntFunction;

/** Original client skills only; no client resource changes or permanent effects. */
public final class BeiDouSkills {
    public record Entry(int id, int level) { }
    public record Resolved(Skill skill, int level) { }

    public static final List<Entry> BLESSING = List.of(
            new Entry(1121000, 30), new Entry(3121002, 30), new Entry(1005, 1),
            new Entry(SuperGM.HASTE, 1), new Entry(SuperGM.BLESS, 1),
            new Entry(SuperGM.HYPER_BODY, 1), new Entry(SuperGM.HOLY_SYMBOL, 1),
            new Entry(5121009, 20), new Entry(1121002, 30),
            new Entry(2321005, 30), new Entry(2121004, 30));

    private BeiDouSkills() { }

    public static List<Resolved> resolve(List<Entry> entries, IntFunction<Skill> resolver) {
        return entries.stream().map(entry -> {
            Skill skill = resolver.apply(entry.id());
            if (skill == null || skill.getId() != entry.id() || skill.getMaxLevel() != entry.level()) {
                throw new IllegalStateException();
            }
            return new Resolved(skill, entry.level());
        }).toList();
    }
}
