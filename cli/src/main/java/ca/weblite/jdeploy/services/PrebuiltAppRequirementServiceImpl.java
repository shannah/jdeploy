package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of PrebuiltAppRequirementService.
 *
 * Determines which platforms require prebuilt apps based on project configuration:
 * - Windows signing enabled → requires prebuilt apps for WIN_X64 and WIN_ARM64
 * - macOS signing enabled (future) → requires prebuilt apps for MAC_X64 and MAC_ARM64
 */
public class PrebuiltAppRequirementServiceImpl implements PrebuiltAppRequirementService {

    @Override
    public boolean requiresPrebuiltApp(JDeployProject project, Platform platform) {
        if (project == null || platform == null) {
            return false;
        }
        return getRequiredPlatforms(project).contains(platform);
    }

    @Override
    public List<Platform> getRequiredPlatforms(JDeployProject project) {
        if (project == null) {
            return Collections.emptyList();
        }

        List<Platform> platforms = new ArrayList<>();

        // Windows signing requires prebuilt apps for Windows platforms
        if (project.isWindowsSigningEnabled()) {
            platforms.add(Platform.WIN_X64);
            platforms.add(Platform.WIN_ARM64);
        }

        // Future: macOS signing would add MAC_X64 and MAC_ARM64
        // if (project.isMacSigningEnabled()) {
        //     platforms.add(Platform.MAC_X64);
        //     platforms.add(Platform.MAC_ARM64);
        // }

        return platforms;
    }

    @Override
    public boolean isPrebuiltAppsEnabled(JDeployProject project) {
        return !getRequiredPlatforms(project).isEmpty();
    }
}
