package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;

import java.util.List;

/**
 * Service to determine when prebuilt apps are needed for a project.
 *
 * Prebuilt apps are automatically enabled when certain features require them,
 * such as Windows code signing. This service encapsulates the logic for
 * determining which platforms require prebuilt apps based on project configuration.
 */
public interface PrebuiltAppRequirementService {

    /**
     * Checks if a prebuilt app is required for the given platform.
     *
     * @param project the jDeploy project configuration
     * @param platform the platform to check
     * @return true if a prebuilt app is required for this platform
     */
    boolean requiresPrebuiltApp(JDeployProject project, Platform platform);

    /**
     * Gets all platforms that require prebuilt apps for the given project.
     *
     * @param project the jDeploy project configuration
     * @return list of platforms requiring prebuilt apps (may be empty)
     */
    List<Platform> getRequiredPlatforms(JDeployProject project);

    /**
     * Checks if prebuilt apps are enabled for the project.
     * This returns true if any feature that requires prebuilt apps is enabled.
     *
     * @param project the jDeploy project configuration
     * @return true if prebuilt apps are enabled
     */
    boolean isPrebuiltAppsEnabled(JDeployProject project);
}
