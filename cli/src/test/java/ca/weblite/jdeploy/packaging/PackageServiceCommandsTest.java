package ca.weblite.jdeploy.packaging;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that jdeploy.commands from package.json are persisted into the jdeploy bundle metadata.
 */
public class PackageServiceCommandsTest {

    @Test
    public void testWriteJdeployMetadataIncludesCommands() throws Exception {
        File tmpDir = Files.createTempDirectory("jdeploy-test").toFile();
        tmpDir.deleteOnExit();

        // Build a packageJsonMap containing a jdeploy.commands entry
        Map<String, Object> pkgMap = new HashMap<>();
        pkgMap.put("name", "testpkg");
        pkgMap.put("version", "1.2.3");

        Map<String, Object> jdeploy = new HashMap<>();
        Map<String, Object> commands = new HashMap<>();
        Map<String, Object> fooSpec = new HashMap<>();
        fooSpec.put("args", Arrays.asList("--flag", "value"));
        commands.put("foo", fooSpec);
        jdeploy.put("commands", commands);
        pkgMap.put("jdeploy", jdeploy);

        PackagingContext context = PackagingContext.builder()
                .directory(tmpDir)
                .packageJsonMap(pkgMap)
                .packageJsonFile(new File(tmpDir, "package.json"))
                .build();

        // ensure the bundle dir exists
        File bundleDir = context.getJdeployBundleDir();
        assertNotNull(bundleDir);
        bundleDir.mkdirs();

        // invoke the metadata writer
        PackageService.writeJdeployMetadata(context);

        File metadataFile = new File(bundleDir, "package-info.json");
        assertTrue(metadataFile.exists(), "package-info.json should exist in the jdeploy bundle");

        String contents = FileUtils.readFileToString(metadataFile, "UTF-8");
        JSONObject obj = new JSONObject(contents);
        assertTrue(obj.has("jdeploy"), "metadata must include jdeploy object");

        JSONObject jdep = obj.getJSONObject("jdeploy");
        assertTrue(jdep.has("commands"), "jdeploy object must include commands");

        JSONObject cmds = jdep.getJSONObject("commands");
        assertTrue(cmds.has("foo"), "commands must include 'foo'");

        JSONObject foo = cmds.getJSONObject("foo");
        assertTrue(foo.has("args"), "foo spec must include args");
        JSONArray args = foo.getJSONArray("args");
        assertEquals(2, args.length());
        assertEquals("--flag", args.getString(0));
        assertEquals("value", args.getString(1));
    }
}
