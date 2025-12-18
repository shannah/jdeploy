#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include <shlobj.h>
#include <ctype.h>

void win_dirname(char *path) {
    char *lastSlash = strrchr(path, '\\');
    if (!lastSlash) {
        lastSlash = strrchr(path, '/');
    }
    if (lastSlash) {
        *lastSlash = '\0';
    }
}

void convertUnixPathToWindows(const char *unixPath, char *windowsPath, size_t size) {
    // Check if the path starts with a forward slash (Unix-style)
    if (unixPath[0] == '/') {
        // Skip leading '/' and convert to 'C:\'
        snprintf(windowsPath, size, "%c:%s", toupper(unixPath[1]), unixPath + 2);
    } else {
        // If the path does not start with '/', assume it's already a Windows path
        strncpy(windowsPath, unixPath, size - 1);
        windowsPath[size - 1] = '\0'; // Ensure null-termination
    }

    // Convert forward slashes to backslashes
    for (size_t i = 0; i < strlen(windowsPath); i++) {
        if (windowsPath[i] == '/') {
            windowsPath[i] = '\\';
        }
    }
}

void loadEnvFile(const char *filePath) {
    FILE *file = fopen(filePath, "r");
    if (file == NULL) {
        return;
    }

    char line[256];
    while (fgets(line, sizeof(line), file)) {
        // Trim newline characters
        line[strcspn(line, "\r\n")] = 0;

        // Split the line into key and value
        char *key = strtok(line, "=");
        char *value = strtok(NULL, "=");

        // Set the environment variable
        if (key && value) {
            _putenv_s(key, value);
        }
    }

    fclose(file);
}

int main(int argc, char *argv[]) {
    char exePath[FILENAME_MAX];
    char scriptPath[FILENAME_MAX];
    char installerJarPath[FILENAME_MAX];
    char javaCommand[FILENAME_MAX * 4]; // Adjust size if necessary
    char *javaHomeUnix = getenv("JAVA_HOME");
    char *jdeployProjectPath = getenv("JDEPLOY_PROJECT_PATH");
    char javaHomeWindows[FILENAME_MAX];
    char *jdeployInstallerArgs = getenv("JDEPLOY_INSTALLER_ARGS");
    char homeWindows[FILENAME_MAX];

    // Get the current user's home directory
    if (SHGetFolderPath(NULL, CSIDL_PROFILE, NULL, 0, homeWindows) != S_OK) {
        fprintf(stderr, "Error getting user's home directory\n");
        return 1;
    }

    // Print JAVA_HOME and HOME for debugging
    printf("JAVA_HOME: %s\n", javaHomeUnix);
    printf("HOME: %s\n", homeWindows);

    // If JAVA_HOME is not set, attempt to load the .env.dev file
    char envFilePath[FILENAME_MAX];
    snprintf(envFilePath, sizeof(envFilePath), "%s\\.jdeploy\\.env.dev", homeWindows);
    loadEnvFile(envFilePath);
    javaHomeUnix = getenv("JAVA_HOME");
    jdeployInstallerArgs = getenv("JDEPLOY_INSTALLER_ARGS");
    jdeployProjectPath = getenv("JDEPLOY_PROJECT_PATH");
    if (jdeployProjectPath == NULL) {
        fprintf(stderr, "Error: JDEPLOY_PROJECT_PATH is not set.  Please set it in %s\n", envFilePath);
        return 1;
    }

    // If JAVA_HOME is still not set, print an error and exit
    if (!javaHomeUnix) {
        fprintf(stderr, "JAVA_HOME is not set.  Please set it in %s\n", envFilePath);
        return 1;
    }

    // Convert JAVA_HOME from Unix-style to Windows-style
    convertUnixPathToWindows(javaHomeUnix, javaHomeWindows, sizeof(javaHomeWindows));

    // Get the path to the current executable
    if (GetModuleFileName(NULL, exePath, sizeof(exePath)) == 0) {
        fprintf(stderr, "Error getting executable path\n");
        return 1;
    }

    // Get the directory path of the current executable
    strcpy(scriptPath, exePath);
    win_dirname(scriptPath);

    // Construct the path to the installer JAR file using HOME
    snprintf(installerJarPath, sizeof(installerJarPath), "%s\\installer\\target\\jdeploy-installer-1.0-SNAPSHOT.jar", jdeployProjectPath);

    // Construct the property arguments
    char propertyAppxmlArg[FILENAME_MAX * 2];
    char propertyLauncherArg[FILENAME_MAX * 2];
    snprintf(propertyAppxmlArg, sizeof(propertyAppxmlArg), "-Dclient4j.appxml.path=\"%s\\.jdeploy-files\\app.xml\"", scriptPath);
    snprintf(propertyLauncherArg, sizeof(propertyLauncherArg), "-Dclient4j.launcher.path=\"%s\"", exePath);

    // Construct the java command
    snprintf(javaCommand, sizeof(javaCommand), "\"%s\\bin\\java\" %s %s -jar %s %s", javaHomeWindows, propertyAppxmlArg, propertyLauncherArg, installerJarPath, jdeployInstallerArgs ? jdeployInstallerArgs : "");

    // Append arguments passed to the program
    for (int i = 1; i < argc; i++) {
        strcat(javaCommand, " ");
        strcat(javaCommand, argv[i]);
    }

    // Print the java command for debugging
    printf("Executing command: %s\n", javaCommand);

    // Set up the process startup information
    STARTUPINFO si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    // Convert the command to a mutable string
    char cmd[FILENAME_MAX * 4];
    strcpy(cmd, javaCommand);

    // Create the process
    if (!CreateProcess(NULL, cmd, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
        fprintf(stderr, "CreateProcess failed (%d).\n", GetLastError());
        return 1;
    }

    // Wait until the child process exits
    WaitForSingleObject(pi.hProcess, INFINITE);

    // Get the exit code
    DWORD exitCode;
    GetExitCodeProcess(pi.hProcess, &exitCode);

    // Close process and thread handles
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    return exitCode;
}