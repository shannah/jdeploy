package com.joshondesign.appbundler.mac;

import com.github.gino0631.icns.IcnsIcons;
import com.github.gino0631.icns.IcnsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the icns slice family that {@link MacBundler} writes for a mac app bundle's icon.
 */
public class MacBundlerIconTest {

    /**
     * macOS renders a PNG payload in these slices scrambled when the icns is an app bundle's
     * icon, so they must never appear in a bundle we build.
     */
    private static final List<String> SCRAMBLED_TYPES = java.util.Arrays.asList("icp4", "icp5", "icp6");

    @TempDir
    File contentsDir;

    private File icnsFile;

    @BeforeEach
    public void setUp() {
        icnsFile = new File(contentsDir, "icon.icns");
    }

    @Test
    public void doesNotEmitTheSlicesMacOsRendersScrambled() throws Exception {
        MacBundler.writeIcns(squareIcon(1024), contentsDir, "ic10", icnsFile);

        for (String osType : osTypesIn(icnsFile)) {
            assertFalse(SCRAMBLED_TYPES.contains(osType),
                    "icns must not contain " + osType + ", which macOS renders scrambled in an app bundle");
        }
    }

    @Test
    public void coversTheSmallSizesWithTheRetinaSlices() throws Exception {
        MacBundler.writeIcns(squareIcon(1024), contentsDir, "ic10", icnsFile);

        List<String> osTypes = osTypesIn(icnsFile);
        // ic11 is 16pt@2x and ic12 is 32pt@2x - the Finder list view and the Force Quit dialog
        // render from these once icp4/icp5 are gone.
        assertTrue(osTypes.contains("ic11"), "expected ic11 (16pt@2x) but got " + osTypes);
        assertTrue(osTypes.contains("ic12"), "expected ic12 (32pt@2x) but got " + osTypes);
        assertEquals(
                java.util.Arrays.asList("ic11", "ic12", "ic07", "ic08", "ic13", "ic09", "ic14", "ic10"),
                osTypes);
    }

    @Test
    public void eachSlicePayloadMatchesTheSizeItsTypeDeclares() throws Exception {
        MacBundler.writeIcns(squareIcon(1024), contentsDir, "ic10", icnsFile);

        try (IcnsIcons icons = IcnsIcons.load(icnsFile.toPath())) {
            for (IcnsIcons.Entry entry : icons.getEntries()) {
                IcnsType type = entry.getType();
                assertNotNull(type, "unknown slice type " + entry.getOsType());
                BufferedImage payload = ImageIO.read(new java.io.ByteArrayInputStream(bytesOf(entry)));
                assertNotNull(payload, entry.getOsType() + " payload is not a readable image");
                assertEquals(type.getWidth(), payload.getWidth(), entry.getOsType() + " width");
                assertEquals(type.getHeight(), payload.getHeight(), entry.getOsType() + " height");
            }
        }
    }

    @Test
    public void doesNotUpscaleASourceSmallerThanTheLargestSlice() throws Exception {
        MacBundler.writeIcns(squareIcon(128), contentsDir, "ic07", icnsFile);

        // ic08/ic13 and up would each be an upscale of the 128px source tagged as native.
        assertEquals(java.util.Arrays.asList("ic11", "ic12", "ic07"), osTypesIn(icnsFile));
    }

    @Test
    public void fallsBackToASingleSliceWhenTheSourceFillsNoneOfThem() throws Exception {
        // A 16x16 source is smaller than ic11, the smallest slice we emit. An icns with no icon
        // at all would be worse than the scrambled one, so the source's own type is kept.
        MacBundler.writeIcns(squareIcon(16), contentsDir, "icp4", icnsFile);

        assertEquals(java.util.Arrays.asList("icp4"), osTypesIn(icnsFile));
    }

    @Test
    public void removesTheIntermediateThumbnails() throws Exception {
        MacBundler.writeIcns(squareIcon(1024), contentsDir, "ic10", icnsFile);

        for (File f : contentsDir.listFiles()) {
            assertFalse(f.getName().startsWith("icon-") && f.getName().endsWith(".png"),
                    "left behind thumbnail " + f.getName());
        }
    }

    private List<String> osTypesIn(File icns) throws Exception {
        List<String> osTypes = new ArrayList<>();
        try (IcnsIcons icons = IcnsIcons.load(icns.toPath())) {
            for (IcnsIcons.Entry entry : icons.getEntries()) {
                osTypes.add(entry.getOsType());
            }
        }

        return osTypes;
    }

    private byte[] bytesOf(IcnsIcons.Entry entry) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = entry.newInputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
        }

        return out.toByteArray();
    }

    private File squareIcon(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.YELLOW);
        g.fillRect(0, 0, size, size);
        g.setColor(Color.BLACK);
        g.fillOval(size / 4, size / 4, size / 2, size / 2);
        g.dispose();
        File iconFile = new File(contentsDir, "icon.png");
        ImageIO.write(image, "png", iconFile);

        return iconFile;
    }
}
