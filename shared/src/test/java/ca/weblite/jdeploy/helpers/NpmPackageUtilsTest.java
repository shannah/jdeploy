package ca.weblite.jdeploy.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NpmPackageUtilsTest {

    @Test
    public void scopedPackageNameStripsScope() {
        assertEquals("cli", NpmPackageUtils.deriveDefaultTitle("@crowdin/cli"));
    }

    @Test
    public void unscopedPackageNameUnchanged() {
        assertEquals("my-app", NpmPackageUtils.deriveDefaultTitle("my-app"));
    }

    @Test
    public void nullReturnsNull() {
        assertNull(NpmPackageUtils.deriveDefaultTitle(null));
    }

    @Test
    public void scopeWithoutSlashUnchanged() {
        assertEquals("@scope", NpmPackageUtils.deriveDefaultTitle("@scope"));
    }

    @Test
    public void trailingSlashUnchanged() {
        assertEquals("@scope/", NpmPackageUtils.deriveDefaultTitle("@scope/"));
    }
}
