package ca.weblite.jdeploy.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class CommandSpecTest {

    @Test
    public void testArgsDefaultToEmptyWhenNull() {
        CommandSpec cs = new CommandSpec("cmd", null);
        assertNotNull(cs.getArgs());
        assertTrue(cs.getArgs().isEmpty());
    }

    @Test
    public void testEqualsAndHashCode() {
        CommandSpec a1 = new CommandSpec("cmd", Arrays.asList("a", "b"));
        CommandSpec a2 = new CommandSpec("cmd", Arrays.asList("a", "b"));
        CommandSpec b = new CommandSpec("cmd", Collections.singletonList("x"));
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
    }

    @Test
    public void testToStringContainsName() {
        CommandSpec cs = new CommandSpec("mycmd", Arrays.asList("--flag"));
        String s = cs.toString();
        assertTrue(s.contains("mycmd"));
        assertTrue(s.contains("--flag"));
    }
}
