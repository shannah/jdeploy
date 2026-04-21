package ca.weblite.jdeploy.publishing.npm;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.BundleChecksumWriter;
import ca.weblite.jdeploy.publishing.BundleUploadRouter;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.DefaultBundleService;
import ca.weblite.jdeploy.services.PackageNameService;
import ca.weblite.jdeploy.services.PlatformBundleGenerator;
import ca.weblite.jdeploy.services.PublishBundleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that NPMPublishDriver.prepare() registers the package name with jdeploy.com
 * so that the download page can resolve a bundle code. See flexganttfxshowcase bug where
 * the registration step was missing for npm-only publishes.
 */
@ExtendWith(MockitoExtension.class)
class NPMPublishDriverRegisterTest {

    @TempDir
    File tempDir;

    @Mock private BasePublishDriver baseDriver;
    @Mock private PlatformBundleGenerator platformBundleGenerator;
    @Mock private DefaultBundleService defaultBundleService;
    @Mock private JDeployProjectFactory projectFactory;
    @Mock private PublishBundleService publishBundleService;
    @Mock private BundleUploadRouter bundleUploadRouter;
    @Mock private BundleChecksumWriter bundleChecksumWriter;
    @Mock private BundleCodeService bundleCodeService;
    @Mock private PublishTargetInterface target;
    @Mock private BundlerSettings bundlerSettings;
    @Mock private NPM npm;

    private NPMPublishDriver driver;
    private PublishingContext publishingContext;

    @BeforeEach
    void setUp() {
        driver = new NPMPublishDriver(
                baseDriver,
                platformBundleGenerator,
                defaultBundleService,
                projectFactory,
                publishBundleService,
                bundleUploadRouter,
                bundleChecksumWriter,
                bundleCodeService,
                new PackageNameService()
        );

        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "flexganttfxshowcase");
        packageJsonMap.put("version", "11.13.4");

        PackagingContext packagingContext = new PackagingContext(
                tempDir,
                packageJsonMap,
                new File(tempDir, "package.json"),
                false,
                false,
                null,
                null,
                null,
                null,
                new PrintStream(System.out),
                new PrintStream(System.err),
                System.in,
                true,
                false
        );

        publishingContext = new PublishingContext(
                packagingContext,
                false,
                npm,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("prepare() registers the package name with jdeploy.com for npm targets")
    void prepareRegistersNpmPackageName() throws IOException {
        when(target.getType()).thenReturn(PublishTargetType.NPM);
        when(publishBundleService.isEnabled(any(PackagingContext.class))).thenReturn(false);

        driver.prepare(publishingContext, target, bundlerSettings);

        verify(baseDriver).prepare(publishingContext, target, bundlerSettings);
        // For an npm target PackageNameService returns the bare package name (no source prefix),
        // which is exactly what the server-side /register endpoint expects.
        verify(bundleCodeService).fetchJdeployBundleCode("flexganttfxshowcase");
    }

    @Test
    @DisplayName("prepare() propagates failures from the register call so the publish fails loudly")
    void prepareFailsWhenRegisterFails() throws IOException {
        when(target.getType()).thenReturn(PublishTargetType.NPM);
        when(publishBundleService.isEnabled(any(PackagingContext.class))).thenReturn(false);
        doThrow(new IOException("register.php unreachable"))
                .when(bundleCodeService).fetchJdeployBundleCode(anyString());

        IOException ex = assertThrows(IOException.class,
                () -> driver.prepare(publishingContext, target, bundlerSettings));

        // Sanity check: the cause surfaces to the caller so we don't silently skip registration.
        verify(bundleCodeService).fetchJdeployBundleCode("flexganttfxshowcase");
    }
}
