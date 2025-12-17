package ca.weblite.jdeploy.installer;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class MainAddToPathTest {

    @Test
    public void testAlreadyInPath() throws Exception {
        File home = Files.createTempDirectory("home-already-path").toFile();
        File localBin = new File(home, ".local/bin");
        assertTrue(localBin.mkdirs());

        String pathEnv = localBin.getAbsolutePath() + ":/usr/bin";
        boolean ok = Main.addToPath(localBin, "/bin/bash", pathEnv, home);
        assertTrue(ok);
    }

    @Test
    public void testBashChooseProfileFile() throws Exception {
        File home = Files.createTempDirectory("home-bash").toFile();
        File bashProfile = new File(home, ".bash_profile");
        // ensure .bash_profile exists and .bashrc does not
        assertTrue(bashProfile.createNewFile());
        File localBin = new File(home, ".local/bin");
        assertTrue(localBin.mkdirs());

        boolean ok = Main.addToPath(localBin, "/bin/bash", "", home);
        assertTrue(ok);

        String content = new String(Files.readAllBytes(bashProfile.toPath()));
        assertTrue(content.contains("$HOME/.local/bin") || content.contains(localBin.getAbsolutePath()));
    }

    @Test
    public void testZshUsesZshrc() throws Exception {
        File home = Files.createTempDirectory("home-zsh").toFile();
        File zshrc = new File(home, ".zshrc");
        // start with no contents
        if (zshrc.exists()) zshrc.delete();
        File localBin = new File(home, ".local/bin");
        assertTrue(localBin.mkdirs());

        boolean ok = Main.addToPath(localBin, "/bin/zsh", "", home);
        assertTrue(ok);

        String content = new String(Files.readAllBytes(zshrc.toPath()));
        assertTrue(content.contains("$HOME/.local/bin") || content.contains(localBin.getAbsolutePath()));
    }
}
