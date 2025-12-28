package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.cli.LinuxCliCommandInstaller;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MainAddToPathTest {

    private List<File> tempDirs = new ArrayList<>();

    @AfterEach
    public void cleanup() throws Exception {
        for (File dir : tempDirs) {
            if (dir.exists()) {
                FileUtils.deleteDirectory(dir);
            }
        }
        tempDirs.clear();
    }

    @Test
    public void testAlreadyInPath() throws Exception {
        File home = Files.createTempDirectory("home-already-path").toFile();
        tempDirs.add(home);
        File localBin = new File(home, ".local/bin");
        assertTrue(localBin.mkdirs());

        String pathEnv = localBin.getAbsolutePath() + ":/usr/bin";
        boolean ok = LinuxCliCommandInstaller.addToPath(localBin, "/bin/bash", pathEnv, home);
        assertTrue(ok);
    }

    @Test
    public void testBashChooseProfileFile() throws Exception {
        File home = Files.createTempDirectory("home-bash").toFile();
        tempDirs.add(home);
        File bashrc = new File(home, ".bashrc");
        File localBin = new File(home, ".local/bin");
        assertTrue(localBin.mkdirs());

        boolean ok = LinuxCliCommandInstaller.addToPath(localBin, "/bin/bash", "", home);
        assertTrue(ok);

        String content = new String(Files.readAllBytes(bashrc.toPath()));
        assertTrue(content.contains("$HOME/.local/bin") || content.contains(localBin.getAbsolutePath()));
    }

    @Test
    public void testZshUsesZshrc() throws Exception {
        File home = Files.createTempDirectory("home-zsh").toFile();
        tempDirs.add(home);
        File zshrc = new File(home, ".zshrc");
        File localBin = new File(home, ".local/bin");
        assertTrue(localBin.mkdirs());

        boolean ok = LinuxCliCommandInstaller.addToPath(localBin, "/bin/zsh", "", home);
        assertTrue(ok);

        String content = new String(Files.readAllBytes(zshrc.toPath()));
        assertTrue(content.contains("$HOME/.local/bin") || content.contains(localBin.getAbsolutePath()));
    }
}
