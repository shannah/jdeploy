package ca.weblite.jdeploy.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class CommandSpecTest {

    @Test
    public void testArgsDefaultToEmptyWhenNull() {
        CommandSpec cs = new CommandSpec("cmd", null, null);
        assertNotNull(cs.getArgs());
        assertTrue(cs.getArgs().isEmpty());
    }

    @Test
    public void testEqualsAndHashCode() {
        CommandSpec a1 = new CommandSpec("cmd", null, Arrays.asList("a", "b"));
        CommandSpec a2 = new CommandSpec("cmd", null, Arrays.asList("a", "b"));
        CommandSpec b = new CommandSpec("cmd", null, Collections.singletonList("x"));
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
    }

    @Test
    public void testToStringContainsName() {
        CommandSpec cs = new CommandSpec("mycmd", null, Arrays.asList("--flag"));
        String s = cs.toString();
        assertTrue(s.contains("mycmd"));
        assertTrue(s.contains("--flag"));
    }

    @Test
    public void testDescriptionCanBeNull() {
        CommandSpec cs = new CommandSpec("cmd", null, Arrays.asList("a"));
        assertNull(cs.getDescription());
    }

    @Test
    public void testDescriptionCanBeNonNull() {
        CommandSpec cs = new CommandSpec("cmd", "A helpful description", Arrays.asList("a"));
        assertEquals("A helpful description", cs.getDescription());
    }

    @Test
    public void testDifferentDescriptionsNotEqual() {
        CommandSpec cs1 = new CommandSpec("cmd", "Description 1", Arrays.asList("a", "b"));
        CommandSpec cs2 = new CommandSpec("cmd", "Description 2", Arrays.asList("a", "b"));
        assertNotEquals(cs1, cs2);
    }

    @Test
    public void testSameDescriptionsEqual() {
        CommandSpec cs1 = new CommandSpec("cmd", "Same description", Arrays.asList("a", "b"));
        CommandSpec cs2 = new CommandSpec("cmd", "Same description", Arrays.asList("a", "b"));
        assertEquals(cs1, cs2);
        assertEquals(cs1.hashCode(), cs2.hashCode());
    }

    @Test
    public void testToStringIncludesDescription() {
        CommandSpec cs = new CommandSpec("mycmd", "My description", Arrays.asList("--flag"));
        String s = cs.toString();
        assertTrue(s.contains("mycmd"));
        assertTrue(s.contains("My description"));
        assertTrue(s.contains("--flag"));
    }

    @Test
    public void testHashCodeDiffersWithDifferentDescription() {
        CommandSpec cs1 = new CommandSpec("cmd", "Desc1", Arrays.asList("a", "b"));
        CommandSpec cs2 = new CommandSpec("cmd", "Desc2", Arrays.asList("a", "b"));
        assertNotEquals(cs1.hashCode(), cs2.hashCode());
    }
}
