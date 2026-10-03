package ca.weblite.jdeploy.appbundler.mac;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The appearance of the DMG installer window.  The defaults match jDeploy's own bundled
 * background image.
 */
public class DmgSettings {

    private static final String[] BACKGROUND_FILE_NAMES = {"dmgbackground.tiff", "dmgbackground.png"};

    private File background;

    private int windowWidth = 500;

    private int windowHeight = 350;

    private int iconSize = 100;

    private int appIconX = 100;

    private int appIconY = 150;

    private int applicationsIconX = 400;

    private int applicationsIconY = 150;

    /**
     * Reads the settings from the "dmg" object of the jdeploy section of package.json, e.g.
     *
     * <pre>
     * "jdeploy": {
     *     "dmg": {
     *         "background": "art/dmg-background.tiff",
     *         "windowSize": [500, 350],
     *         "iconSize": 100,
     *         "appIconPosition": [100, 150],
     *         "applicationsIconPosition": [400, 150]
     *     }
     * }
     * </pre>
     *
     * If no background is configured there, a dmgbackground.tiff or dmgbackground.png file in
     * the project directory is used.  Properties that are absent keep their default value.
     *
     * @param jdeployConfig the jdeploy object of package.json.  May be null.
     * @param projectDirectory the directory that a relative background path is resolved against.
     */
    public static DmgSettings fromPackageJson(Map jdeployConfig, File projectDirectory) {
        Object dmg = jdeployConfig == null ? null : jdeployConfig.get("dmg");
        Map config = dmg instanceof Map ? (Map) dmg : Collections.emptyMap();
        DmgSettings out = new DmgSettings();
        out.background = findBackground(config.get("background"), projectDirectory);
        int[] windowSize = intPair(config, "windowSize");
        if (windowSize != null) {
            out.windowWidth = windowSize[0];
            out.windowHeight = windowSize[1];
        }
        if (config.get("iconSize") instanceof Number) {
            out.iconSize = ((Number) config.get("iconSize")).intValue();
        }
        int[] appIconPosition = intPair(config, "appIconPosition");
        if (appIconPosition != null) {
            out.appIconX = appIconPosition[0];
            out.appIconY = appIconPosition[1];
        }
        int[] applicationsIconPosition = intPair(config, "applicationsIconPosition");
        if (applicationsIconPosition != null) {
            out.applicationsIconX = applicationsIconPosition[0];
            out.applicationsIconY = applicationsIconPosition[1];
        }
        return out;
    }

    private static File findBackground(Object configuredPath, File projectDirectory) {
        if (configuredPath instanceof String) {
            File file = new File((String) configuredPath);
            if (!file.isAbsolute()) {
                file = new File(projectDirectory, (String) configuredPath);
            }
            if (!file.isFile()) {
                throw new IllegalArgumentException("DMG background image not found: " + file);
            }
            return file;
        }
        for (String name : BACKGROUND_FILE_NAMES) {
            File file = new File(projectDirectory, name);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private static int[] intPair(Map config, String property) {
        Object value = config.get(property);
        if (value == null) {
            return null;
        }
        if (value instanceof List && ((List) value).size() == 2) {
            List list = (List) value;
            if (list.get(0) instanceof Number && list.get(1) instanceof Number) {
                return new int[]{((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue()};
            }
        }
        throw new IllegalArgumentException(
                "jdeploy.dmg." + property + " must be an array of two numbers.  E.g. [500, 350]"
        );
    }

    /**
     * The background image for the installer window, or null to use jDeploy's own.
     */
    public File getBackground() {
        return background;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public int getIconSize() {
        return iconSize;
    }

    public int getAppIconX() {
        return appIconX;
    }

    public int getAppIconY() {
        return appIconY;
    }

    public int getApplicationsIconX() {
        return applicationsIconX;
    }

    public int getApplicationsIconY() {
        return applicationsIconY;
    }
}
