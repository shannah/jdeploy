package ca.weblite.jdeploy.models;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommandSpecParser.
 * Tests cover valid command parsing, invalid command rejection, empty handling,
 * missing args, and deterministic sorting.
 */
public class CommandSpecParserTest {

    @Test
    void testParseCommands_validSingleCommand() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONArray args = new JSONArray();
        args.put("--some-flag");
        args.put("--another-flag");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("mycommand", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals("mycommand", spec.getName());
        assertEquals(2, spec.getArgs().size());
        assertEquals("--some-flag", spec.getArgs().get(0));
        assertEquals("--another-flag", spec.getArgs().get(1));
    }

    @Test
    void testParseCommands_multipleValidCommands() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args1 = new JSONArray();
        args1.put("--flag1");
        JSONObject cmd1 = new JSONObject();
        cmd1.put("args", args1);
        commands.put("cmd1", cmd1);
        
        JSONArray args2 = new JSONArray();
        args2.put("--flag2");
        args2.put("--flag3");
        JSONObject cmd2 = new JSONObject();
        cmd2.put("args", args2);
        commands.put("cmd2", cmd2);
        
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(2, result.size());
        
        // Commands should be sorted deterministically by name
        CommandSpec first = result.get(0);
        CommandSpec second = result.get(1);
        assertTrue(first.getName().compareTo(second.getName()) <= 0);
    }

    @Test
    void testParseCommands_validCommandNamesWithAllowedCharacters() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Test various valid command name patterns
        commands.put("my-command", new JSONObject());
        commands.put("my_command", new JSONObject());
        commands.put("MyCommand", new JSONObject());
        commands.put("cmd123", new JSONObject());
        commands.put("a", new JSONObject());
        commands.put("1test", new JSONObject());
        commands.put("test.cmd", new JSONObject());
        
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(7, result.size());
        // All names should be present
        List<String> names = result.stream().map(CommandSpec::getName).collect(Collectors.toList());
        assertTrue(names.contains("my-command"));
        assertTrue(names.contains("my_command"));
        assertTrue(names.contains("MyCommand"));
        assertTrue(names.contains("cmd123"));
        assertTrue(names.contains("a"));
        assertTrue(names.contains("1test"));
        assertTrue(names.contains("test.cmd"));
    }

    @Test
    void testParseCommands_invalidCommandNameRejected() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Invalid: starts with hyphen
        commands.put("-invalid", new JSONObject());
        jdeployConfig.put("commands", commands);

        assertThrows(Exception.class, () -> CommandSpecParser.parseCommands(jdeployConfig));
    }

    @Test
    void testParseCommands_invalidCommandNameWithSpace() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Invalid: contains space
        commands.put("invalid command", new JSONObject());
        jdeployConfig.put("commands", commands);

        assertThrows(Exception.class, () -> CommandSpecParser.parseCommands(jdeployConfig));
    }

    @Test
    void testParseCommands_invalidCommandNameWithSpecialCharacters() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Invalid: contains special characters like @ or #
        commands.put("invalid@cmd", new JSONObject());
        jdeployConfig.put("commands", commands);

        assertThrows(Exception.class, () -> CommandSpecParser.parseCommands(jdeployConfig));
    }

    @Test
    void testParseCommands_emptyCommandsObject() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(0, result.size());
    }

    @Test
    void testParseCommands_noCommandsKey() {
        JSONObject jdeployConfig = new JSONObject();

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(0, result.size());
    }

    @Test
    void testParseCommands_commandWithMissingArgs() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Command with null value (invalid)
        commands.put("mycmd", JSONObject.NULL);
        jdeployConfig.put("commands", commands);

        // Parser should reject null command values
        assertThrows(IllegalArgumentException.class, () -> CommandSpecParser.parseCommands(jdeployConfig));
    }

    @Test
    void testParseCommands_commandWithEmptyArgsArray() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray emptyArgs = new JSONArray();
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", emptyArgs);
        commands.put("mycmd", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals("mycmd", spec.getName());
        assertEquals(0, spec.getArgs().size());
    }

    @Test
    void testParseCommands_deterministicSorting() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Add commands in non-alphabetical order
        commands.put("zebra", new JSONObject());
        commands.put("apple", new JSONObject());
        commands.put("banana", new JSONObject());
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result1 = CommandSpecParser.parseCommands(jdeployConfig);
        List<CommandSpec> result2 = CommandSpecParser.parseCommands(jdeployConfig);

        // Both parses should produce same order
        assertEquals(result1.size(), result2.size());
        for (int i = 0; i < result1.size(); i++) {
            assertEquals(result1.get(i).getName(), result2.get(i).getName());
        }

        // Commands should be alphabetically sorted
        assertEquals("apple", result1.get(0).getName());
        assertEquals("banana", result1.get(1).getName());
        assertEquals("zebra", result1.get(2).getName());
    }

    @Test
    void testParseCommands_commandWithMultipleArgs() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--jvm-arg");
        args.put("-Xmx1024m");
        args.put("--main-class=com.example.Main");
        args.put("${args}");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("myapp", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals("myapp", spec.getName());
        assertEquals(4, spec.getArgs().size());
        assertEquals("--jvm-arg", spec.getArgs().get(0));
        assertEquals("-Xmx1024m", spec.getArgs().get(1));
        assertEquals("--main-class=com.example.Main", spec.getArgs().get(2));
        assertEquals("${args}", spec.getArgs().get(3));
    }

    @Test
    void testParseCommands_immutabilityOfArgs() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--flag");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);
        CommandSpec spec = result.get(0);

        // Verify args list is unmodifiable
        assertThrows(Exception.class, () -> spec.getArgs().add("--new-flag"));
    }

    @Test
    void testParseCommands_argWithSemicolonRejected() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--flag; rm -rf /");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("dangerous shell metacharacters"));
    }

    @Test
    void testParseCommands_argWithPipeRejected() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--output | cat /etc/passwd");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("dangerous shell metacharacters"));
    }

    @Test
    void testParseCommands_argWithAmpersandRejected() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--flag && malicious");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("dangerous shell metacharacters"));
    }

    @Test
    void testParseCommands_argWithBacktickRejected() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--flag=`whoami`");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("dangerous shell metacharacters"));
    }

    @Test
    void testParseCommands_argWithDollarParenRejected() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--flag=$(whoami)");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("dangerous shell metacharacters"));
    }

    @Test
    void testParseCommands_argWithSafeDollarSignAllowed() {
        // $ alone (not followed by parenthesis) should be allowed for variable placeholders like ${args}
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("${args}");
        args.put("$HOME");
        args.put("-Dprop=$VALUE");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getArgs().size());
        assertEquals("${args}", result.get(0).getArgs().get(0));
        assertEquals("$HOME", result.get(0).getArgs().get(1));
        assertEquals("-Dprop=$VALUE", result.get(0).getArgs().get(2));
    }

    @Test
    void testParseCommands_argWithSafeSpecialCharsAllowed() {
        // Safe special chars like =, -, _, ., :, / should be allowed
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--main-class=com.example.Main");
        args.put("-Xmx1024m");
        args.put("/path/to/file");
        args.put("--config:value");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        assertEquals(4, result.get(0).getArgs().size());
    }

    @Test
    void testParseCommands_errorMessageIncludesArgIndex() {
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("--safe-flag");
        args.put("--also-safe");
        args.put("--bad; injection");  // index 2
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("mycmd", commandSpec);
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("mycmd"));
        assertTrue(ex.getMessage().contains("index 2"));
    }

    @Test
    void testParseCommands_commandNameWithMaximumLength() {
        // Test that a 255-character command name (max reasonable length) is accepted
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        StringBuilder longName = new StringBuilder();
        // Start with alphanumeric, then add 254 more valid characters (alphanumeric, dot, dash, underscore)
        longName.append('a');
        for (int i = 1; i < 255; i++) {
            longName.append('x');
        }
        String maxLengthName = longName.toString();
        assertEquals(255, maxLengthName.length());
        
        commands.put(maxLengthName, new JSONObject());
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        assertEquals(maxLengthName, result.get(0).getName());
    }

    @Test
    void testParseCommands_unicodeCharactersInCommandNameRejected() {
        // Test that unicode characters (non-ASCII) are rejected in command names
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Unicode characters like é, ñ, 中文 should be rejected
        commands.put("commandé", new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_commandNameStartingWithDotRejected() {
        // Test that command names starting with a dot are rejected
        // (regex requires starting with alphanumeric: ^[A-Za-z0-9][A-Za-z0-9._-]*$)
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        commands.put(".hidden", new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_commandNameWithDotInMiddleAllowed() {
        // Test that command names with dots in the middle are allowed
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        commands.put("my.command", new JSONObject());
        commands.put("a.b.c", new JSONObject());
        commands.put("test.123", new JSONObject());
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(3, result.size());
        List<String> names = result.stream().map(CommandSpec::getName).collect(Collectors.toList());
        assertTrue(names.contains("my.command"));
        assertTrue(names.contains("a.b.c"));
        assertTrue(names.contains("test.123"));
    }

    // ===== Edge-case security tests =====

    @Test
    void testParseCommands_unicodeHomoglyphAttack_cyrillicA() {
        // Test that command names with Cyrillic 'а' (U+0430) which looks like ASCII 'a' are rejected
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Cyrillic 'а' (U+0430) looks identical to ASCII 'a' but is a different character
        String homoglyphName = "\u0430pple";  // Cyrillic a + "pple"
        commands.put(homoglyphName, new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_unicodeHomoglyphAttack_greekO() {
        // Test that command names with Greek omicron (U+03BF) which looks like ASCII 'o' are rejected
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Greek omicron (U+03BF) looks identical to ASCII 'o'
        String homoglyphName = "c\u03bfmmand";  // "c" + Greek o + "mmand"
        commands.put(homoglyphName, new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_unicodeHomoglyphAttack_cyrillic_в() {
        // Test rejection of Cyrillic 'в' (U+0432) which looks like ASCII 'B'
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        String homoglyphName = "cmd\u0432uild";  // "cmd" + Cyrillic в + "uild"
        commands.put(homoglyphName, new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_emptyStringArgumentAllowed() {
        // Test that empty string arguments are allowed (safe, just represents empty parameter)
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("");
        args.put("--flag");
        args.put("");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals(3, spec.getArgs().size());
        assertEquals("", spec.getArgs().get(0));
        assertEquals("--flag", spec.getArgs().get(1));
        assertEquals("", spec.getArgs().get(2));
    }

    @Test
    void testParseCommands_whitespaceOnlyArgumentAllowed() {
        // Test that whitespace-only arguments are allowed (safe, just whitespace)
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        args.put("   ");
        args.put("\t");
        args.put("\n");
        args.put("  \t  ");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals(4, spec.getArgs().size());
        assertEquals("   ", spec.getArgs().get(0));
        assertEquals("\t", spec.getArgs().get(1));
        assertEquals("\n", spec.getArgs().get(2));
        assertEquals("  \t  ", spec.getArgs().get(3));
    }

    @Test
    void testParseCommands_extremelyLongArgumentList() {
        // Test that parsing handles 100+ arguments without stack overflow or performance issues
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        int argCount = 150;
        for (int i = 0; i < argCount; i++) {
            args.put("--arg-" + i);
        }
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        long startTime = System.currentTimeMillis();
        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals(argCount, spec.getArgs().size());
        
        // Parsing should complete quickly (not take more than 5 seconds)
        assertTrue(elapsedTime < 5000, "Parsing of 150 arguments took too long: " + elapsedTime + "ms");
    }

    @Test
    void testParseCommands_extremelyLongArgumentString() {
        // Test that parsing handles 10KB+ argument strings gracefully
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Create a 10KB+ safe argument string
        StringBuilder longArg = new StringBuilder();
        longArg.append("--very-long-argument=");
        // Fill with safe characters: alphanumerics and hyphens
        for (int i = 0; i < 10240; i++) {
            longArg.append('a');
        }
        
        JSONArray args = new JSONArray();
        args.put(longArg.toString());
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        long startTime = System.currentTimeMillis();
        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals(1, spec.getArgs().size());
        assertEquals(10240 + 21, spec.getArgs().get(0).length());  // 10240 'a's + "--very-long-argument=" prefix
        
        // Should complete quickly even with large strings
        assertTrue(elapsedTime < 5000, "Parsing of 10KB+ argument took too long: " + elapsedTime + "ms");
    }

    @Test
    void testParseCommands_multipleExtremeLongArgumentStrings() {
        // Test handling multiple large argument strings together
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONArray args = new JSONArray();
        for (int i = 0; i < 10; i++) {
            StringBuilder longArg = new StringBuilder();
            longArg.append("--arg-").append(i).append("=");
            // Fill with safe characters
            for (int j = 0; j < 5120; j++) {  // 5KB each
                longArg.append('x');
            }
            args.put(longArg.toString());
        }
        
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        long startTime = System.currentTimeMillis();
        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals(10, spec.getArgs().size());
        
        // Should handle 10 x 5KB arguments gracefully
        assertTrue(elapsedTime < 5000, "Parsing of 10x5KB arguments took too long: " + elapsedTime + "ms");
    }

    @Test
    void testParseCommands_dangerousCharsRejectedInLongArgs() {
        // Test that dangerous characters are still detected even in very long argument strings
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Create a long argument with a semicolon somewhere in the middle
        StringBuilder longArg = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            longArg.append('a');
        }
        longArg.append("; malicious");  // Injection attempt in the middle
        for (int i = 0; i < 5000; i++) {
            longArg.append('b');
        }
        
        JSONArray args = new JSONArray();
        args.put(longArg.toString());
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("dangerous shell metacharacters"));
    }

    @Test
    void testParseCommands_commandNameWithMixedUnicode() {
        // Test that mixed unicode and ASCII in command name is rejected
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Mix of ASCII and Chinese characters
        commands.put("cmd\u4E2D\u6587", new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_zeroWidthCharactersInCommandName() {
        // Test that zero-width characters in command names are rejected
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Zero-width joiner (U+200D) inserted in a name
        String nameWithZeroWidth = "cmd\u200Dtest";
        commands.put(nameWithZeroWidth, new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_rightToLeftOverrideInCommandName() {
        // Test that right-to-left override (U+202E) in command names is rejected
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        // Right-to-left override character
        String nameWithRLO = "cmd\u202Emalicious";
        commands.put(nameWithRLO, new JSONObject());
        jdeployConfig.put("commands", commands);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> CommandSpecParser.parseCommands(jdeployConfig));
        assertTrue(ex.getMessage().contains("Invalid command name"));
    }

    @Test
    void testParseCommands_allDangerousCharactersDetected() {
        // Comprehensive test verifying all dangerous characters are caught
        String[] dangerousChars = {";", "|", "&", "`", "$("};
        
        for (String dangerous : dangerousChars) {
            JSONObject jdeployConfig = new JSONObject();
            JSONObject commands = new JSONObject();
            
            JSONArray args = new JSONArray();
            args.put("--safe" + dangerous + "injection");
            JSONObject commandSpec = new JSONObject();
            commandSpec.put("args", args);
            commands.put("cmd", commandSpec);
            jdeployConfig.put("commands", commands);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> CommandSpecParser.parseCommands(jdeployConfig),
                "Failed to reject dangerous character: " + dangerous);
            assertTrue(ex.getMessage().contains("dangerous shell metacharacters"),
                "Error message missing for character: " + dangerous);
        }
    }

    @Test
    void testParseCommands_veryDeepCommandHierarchyNotSupported() {
        // Verify that commands with nested objects (which parser doesn't support) are rejected
        JSONObject jdeployConfig = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONObject nestedSpec = new JSONObject();
        JSONObject nestedLevel2 = new JSONObject();
        nestedLevel2.put("deep", new JSONObject());
        nestedSpec.put("nested", nestedLevel2);
        commands.put("cmd", nestedSpec);
        jdeployConfig.put("commands", commands);

        // Should parse successfully (extra fields are ignored by the parser)
        List<CommandSpec> result = CommandSpecParser.parseCommands(jdeployConfig);
        assertEquals(1, result.size());
    }
}
