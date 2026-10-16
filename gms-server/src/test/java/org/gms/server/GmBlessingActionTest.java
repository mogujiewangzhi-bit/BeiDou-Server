package org.gms.server;

import org.gms.client.Character;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.constants.skills.SuperGM;
import org.gms.net.packet.Packet;
import org.gms.util.PacketCreator;
import org.gms.manager.ServerManager;
import org.gms.service.GachaponService;
import org.springframework.context.ConfigurableApplicationContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GmBlessingActionTest {
    @BeforeAll
    static void initialize() throws Exception {
        Method bootstrap = Class.forName("org.gms.service.OfflineGrantServiceTest").getDeclaredMethod("bootstrapMessages");
        bootstrap.setAccessible(true);
        bootstrap.invoke(null);
        ((ConfigurableApplicationContext) ServerManager.getApplicationContext()).getBeanFactory()
                .registerSingleton("gachaponService", mock(GachaponService.class));
    }

    private static boolean apply(Character chr) throws Exception {
        Method method = StatEffect.class.getDeclaredMethod("applyBeiDouBlessing", Character.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, chr);
    }

    private static Character player() {
        Character chr = mock(Character.class);
        when(chr.isGM()).thenReturn(true);
        when(chr.isAlive()).thenReturn(true);
        when(chr.getSkillLevel(SuperGM.BLESS)).thenReturn(1);
        return chr;
    }

    private static Skill skill(int id) throws Exception {
        BeiDouSkills.Entry entry = BeiDouSkills.BLESSING.stream().filter(e -> e.id() == id).findFirst().orElseThrow();
        StatEffect effect = new StatEffect();
        Field statups = StatEffect.class.getDeclaredField("statups");
        statups.setAccessible(true);
        statups.set(effect, new ArrayList<>());
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getMaxLevel()).thenReturn(entry.level());
        when(skill.getEffect(entry.level())).thenReturn(effect);
        return skill;
    }

    @Test
    void successfulAndRepeatedCastsReleaseActionsAfterEffects() throws Exception {
        Character chr = player();
        Packet release = mock(Packet.class), visual = mock(Packet.class);
        try (var packets = mockStatic(PacketCreator.class); var skills = mockStatic(SkillFactory.class)) {
            packets.when(PacketCreator::enableActions).thenReturn(release);
            packets.when(() -> PacketCreator.showOwnBuffEffect(SuperGM.BLESS, 1)).thenReturn(visual);
            skills.when(() -> SkillFactory.getSkill(anyInt())).thenAnswer(call -> skill(call.getArgument(0)));
            assertTrue(apply(chr));
            assertTrue(apply(chr));
            var order = inOrder(chr);
            order.verify(chr).sendPacket(visual);
            order.verify(chr).sendPacket(release);
            order.verify(chr).sendPacket(visual);
            order.verify(chr).sendPacket(release);
            verify(chr, times(2)).sendPacket(release);
        }
    }

    @Test
    void rejectedCastReleasesActionsOnce() throws Exception {
        Character chr = player();
        when(chr.isGM()).thenReturn(false);
        Packet release = mock(Packet.class);
        try (var packets = mockStatic(PacketCreator.class)) {
            packets.when(PacketCreator::enableActions).thenReturn(release);
            assertFalse(apply(chr));
            verify(chr).sendPacket(release);
            packets.verify(PacketCreator::enableActions);
            packets.verifyNoMoreInteractions();
        }
    }

    @Test
    void missingSkillReleasesActionsWithoutApplyingEffects() throws Exception {
        Character chr = player();
        Packet release = mock(Packet.class);
        try (var packets = mockStatic(PacketCreator.class); var skills = mockStatic(SkillFactory.class)) {
            packets.when(PacketCreator::enableActions).thenReturn(release);
            assertFalse(apply(chr));
            verify(chr).sendPacket(release);
            verify(chr, never()).cancelEffect(any(StatEffect.class), anyBoolean(), anyLong());
        }
    }

    @Test
    void applicationExceptionReleasesActionsAndPropagatesFailure() throws Exception {
        Character chr = player();
        Packet release = mock(Packet.class);
        RuntimeException failure = new RuntimeException();
        doThrow(failure).when(chr).cancelEffect(any(StatEffect.class), anyBoolean(), anyLong());
        try (var packets = mockStatic(PacketCreator.class); var skills = mockStatic(SkillFactory.class)) {
            packets.when(PacketCreator::enableActions).thenReturn(release);
            skills.when(() -> SkillFactory.getSkill(anyInt())).thenAnswer(call -> skill(call.getArgument(0)));
            InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> apply(chr));
            assertSame(failure, exception.getCause());
            verify(chr).sendPacket(release);
        }
    }
}
