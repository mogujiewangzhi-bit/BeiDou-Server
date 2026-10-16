package org.gms.server;

import org.gms.client.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GmBlessingTest {
    static Stream<BeiDouSkills.Entry> entries() { return BeiDouSkills.BLESSING.stream(); }

    @ParameterizedTest
    @MethodSource("entries")
    void originalClientSkillResourcesExist(BeiDouSkills.Entry entry) throws Exception {
        String file = entry.id() < 10000 ? "000" : Integer.toString(entry.id() / 10000);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(Path.of("wz", "Skill.wz", file + ".img.xml").toFile());
        Element levels = (Element) XPathFactory.newInstance().newXPath().evaluateExpression(
                "//imgdir[@name='skill']/imgdir[@name='" + String.format("%07d", entry.id())
                        + "']/imgdir[@name='level']", document, Node.class);
        assertNotNull(levels);
        NodeList children = levels.getChildNodes();
        int count = 0;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && child.getTagName().equals("imgdir")) { count++; }
        }
        assertEquals(entry.level(), count);
    }

    @Test
    void validatesEntireImmutablePlanBeforeApplication() {
        assertEquals(11, BeiDouSkills.BLESSING.size());
        assertEquals(11, BeiDouSkills.BLESSING.stream().map(BeiDouSkills.Entry::id).distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> BeiDouSkills.BLESSING.clear());
        List<BeiDouSkills.Resolved> applied = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> BeiDouSkills.resolve(BeiDouSkills.BLESSING, id -> {
            if (id == BeiDouSkills.BLESSING.getLast().id()) { return null; }
            Skill skill = new Skill(id);
            int max = BeiDouSkills.BLESSING.stream().filter(e -> e.id() == id).findFirst().orElseThrow().level();
            for (int i = 0; i < max; i++) { skill.addLevelEffect(null); }
            return skill;
        }).forEach(applied::add));
        assertTrue(applied.isEmpty());
    }

    @Test
    void durationCopyDoesNotModifySharedSource() throws Exception {
        StatEffect original = new StatEffect();
        Field duration = StatEffect.class.getDeclaredField("duration");
        duration.setAccessible(true);
        duration.setInt(original, 60_000);
        Field statups = StatEffect.class.getDeclaredField("statups");
        statups.setAccessible(true);
        statups.set(original, new ArrayList<>());
        Method copyMethod = StatEffect.class.getDeclaredMethod("forBeiDouBlessing");
        copyMethod.setAccessible(true);
        StatEffect copy = (StatEffect) copyMethod.invoke(original);
        assertEquals(900_000, copy.getBuffLocalDuration());
        assertEquals(60_000, duration.getInt(original));
        assertTrue(copy.isBeiDouBlessingSource());
        assertFalse(original.isBeiDouBlessingSource());
        assertNotSame(statups.get(original), statups.get(copy));
    }
}
