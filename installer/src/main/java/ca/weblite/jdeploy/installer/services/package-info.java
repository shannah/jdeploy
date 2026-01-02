/**
 * Services for the jDeploy installer module.
 *
 * <p>This package contains service classes that provide high-level functionality for the installer,
 * such as detecting installed applications and managing installation state.</p>
 *
 * <h2>Installation Detection</h2>
 * <p>The {@link ca.weblite.jdeploy.installer.services.InstallationDetectionService} provides
 * methods to check if a jDeploy application is already installed on the system.</p>
 *
 * <h3>Example Usage:</h3>
 * <pre>
 * {@code
 * // Create the service
 * InstallationDetectionService detectionService = new InstallationDetectionService();
 *
 * // Check if an NPM package is installed
 * boolean isInstalled = detectionService.isInstalled("my-app", null);
 * if (isInstalled) {
 *     System.out.println("Application is already installed");
 * }
 *
 * // Check if a GitHub package is installed
 * String githubSource = "https://github.com/user/repo";
 * boolean isGitHubAppInstalled = detectionService.isInstalled("my-app", githubSource);
 *
 * // Check if a specific version is installed
 * boolean isVersionInstalled = detectionService.isInstalled("my-app", "1.0.0", null);
 * }
 * </pre>
 */
package ca.weblite.jdeploy.installer.services;
