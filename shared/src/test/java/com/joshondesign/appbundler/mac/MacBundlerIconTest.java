package com.joshondesign.appbundler.mac;

import com.github.gino0631.icns.IcnsIcons;
import com.github.gino0631.icns.IcnsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
    public void insetsFullBleedArtworkOntoTheIconGrid() throws Exception {
        MacBundler.writeIcns(squareIcon(1024), contentsDir, "ic10", icnsFile);

        // Apple's macOS template centres an 824x824 body in a 1024x1024 canvas. Artwork drawn
        // edge to edge renders a quarter wider than every system icon beside it.
        Rectangle artwork = artworkBoundsOf("ic10");
        assertEquals(824, artwork.width, 12, "artwork width on the icon grid");
        assertEquals(824, artwork.height, 12, "artwork height on the icon grid");
        assertEquals(artwork.x, 1024 - (artwork.x + artwork.width), 2, "artwork is not centred");
    }

    @Test
    public void leavesArtworkThatAlreadySitsOnTheGridAlone() throws Exception {
        // An icon authored to Apple's template already carries the margin; insetting it again
        // would make it smaller than its neighbours instead of the same size.
        MacBundler.writeIcns(insetIcon(1024, 700), contentsDir, "ic10", icnsFile);

        Rectangle artwork = artworkBoundsOf("ic10");
        assertEquals(700, artwork.width, 4, "artwork width should be untouched");
        assertEquals(700, artwork.height, 4, "artwork height should be untouched");
    }

    @Test
    public void removesTheGridFittedSource() throws Exception {
        MacBundler.writeIcns(squareIcon(1024), contentsDir, "ic10", icnsFile);

        assertFalse(new File(contentsDir, MacBundler.GRID_ICON_NAME).exists(),
                "left behind " + MacBundler.GRID_ICON_NAME);
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

    /** Bounds of the non-transparent pixels in the named slice's payload. */
    private Rectangle artworkBoundsOf(String osType) throws Exception {
        try (IcnsIcons icons = IcnsIcons.load(icnsFile.toPath())) {
            for (IcnsIcons.Entry entry : icons.getEntries()) {
                if (!entry.getOsType().equals(osType)) continue;
                BufferedImage payload = ImageIO.read(new java.io.ByteArrayInputStream(bytesOf(entry)));
                assertNotNull(payload, osType + " payload is not a readable image");

                return opaqueBounds(payload);
            }
        }
        throw new AssertionError("no " + osType + " slice in " + icnsFile);
    }

    private Rectangle opaqueBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) < 25) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        assertTrue(maxX >= 0, "payload is fully transparent");

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** An icon whose artwork occupies {@code artworkSize} of a {@code size} canvas, centred. */
    private File insetIcon(int size, int artworkSize) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        int origin = (size - artworkSize) / 2;
        g.setColor(Color.YELLOW);
        g.fillRect(origin, origin, artworkSize, artworkSize);
        g.dispose();
        File iconFile = new File(contentsDir, "icon.png");
        ImageIO.write(image, "png", iconFile);

        return iconFile;
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
