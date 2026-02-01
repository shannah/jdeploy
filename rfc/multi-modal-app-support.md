# RFC: Multi-Modal App Support in jDeploy v6

## Overview / Motivation

Previous versions of jDeploy bundled only GUI desktop applications. jDeploy v6 extends the installer to support multiple application modes within a single package: GUI apps, CLI commands, background services/daemons, a system tray helper, self-updating commands, CLI-to-GUI launchers, and MCP servers for AI tool integration.

Goals:
- Allow a single `package.json` to declare and install multiple application modes alongside the GUI app.
- Provide a clean public API (system properties, `package.json` configuration) for Java developers to detect and respond to modes at runtime.
- Keep launcher internals (argument stripping, service lifecycle, update mechanics) hidden from application code.

Non-goals:
- Changing the default GUI-only behavior for existing packages.
- Requiring developers to adopt multi-modal support -- all new features are opt-in.

---

## Runtime Mode Detection

The jDeploy native launcher passes a system property `-Djdeploy.mode=<mode>` to the JVM. Two values are defined:

| `jdeploy.mode` | Meaning |
|-----------------|---------|
| `gui` | Default GUI application |
| `command` | CLI command (includes service controllers and MCP servers) |

### System Properties Injected by the Launcher

| Property | Description |
|----------|-------------|
| `jdeploy.mode` | Mode: `gui` or `command` |
| `jdeploy.launcher.path` | Absolute path to the launcher executable |
| `jdeploy.launcher.app.version` | Launcher version |
| `jdeploy.app.version` | Application version |
| `jdeploy.app.source` | Package source (NPM registry URL or GitHub URL) |
| `jdeploy.app.name` | Application package name |
| `jdeploy.prerelease` | `true` if this is a prerelease version |
| `jdeploy.build.mode` | `release` or `debug` |
| `jdeploy.registry.url` | jDeploy registry URL |
| `jdeploy.commitHash` | Git commit hash of the build |
| `jdeploy.updatesAvailable` | `true` if updates are available |
| `jdeploy.config.file` | Path to the runtime configuration file |
| `jdeploy.background` | `true` if running in background mode |
| `jdeploy.singleton.ipcdir` | IPC directory for singleton mode |
| `jdeploy.singleton.openFiles` | JSON array of files opened in singleton mode |
| `jdeploy.singleton.openURIs` | JSON array of URIs opened in singleton mode |

The launcher also supports **conditional platform arguments** in `package.json`:
- `-D[mac]property=value` -- only included on macOS
- `-D[win]property=value` -- only included on Windows
- `-D[linux]property=value` -- only included on Linux
- `-D[mac|linux]property=value` -- included on macOS or Linux

---

## Mode Specifications

### 1. GUI Mode (Default)

The default mode. When the user launches the installed application normally (double-click, Start menu, Dock, etc.), it starts as a desktop GUI application. The launcher sets `jdeploy.mode=gui`, initializes the platform GUI subsystem, and shows the splash screen (if configured).

No special configuration is needed -- this is the behavior jDeploy has always provided.

#### package.json

```json
{
  "name": "my-gui-app",
  "version": "1.0.0",
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My GUI App"
  }
}
```

#### Java Detection

```java
public static void main(String[] args) {
    String mode = System.getProperty("jdeploy.mode", "gui");

    if ("gui".equals(mode)) {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
```

---

### 2. CLI Command Mode

Defines named command-line tools that are installed to the user's PATH. When invoked, the launcher sets `jdeploy.mode=command`, skips GUI initialization (no AWT, Swing, JavaFX, AppKit, splash screen), and connects stdin/stdout/stderr to the calling terminal. Exit codes are forwarded.

Each command is a key in the `jdeploy.commands` object. The installer creates platform-appropriate wrapper scripts that invoke the launcher in command mode. The Java app's `main(String[] args)` receives only the user-supplied arguments -- all launcher internals are stripped.

#### package.json

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My App",
    "commands": {
      "myapp-cli": {
        "description": "Run My App in command-line mode",
        "args": ["-Dapp.role=cli"]
      },
      "myapp-convert": {
        "description": "Convert files between formats",
        "args": ["--converter"]
      }
    }
  }
}
```

#### Java Detection

```java
public static void main(String[] args) {
    String mode = System.getProperty("jdeploy.mode", "gui");

    switch (mode) {
        case "command":
            // Running as a CLI command -- no GUI.
            // args contains ONLY the user-supplied arguments.
            // e.g. "myapp-cli foo bar" -> args = ["foo", "bar"]
            runCli(Arrays.asList(args));
            break;
        case "gui":
        default:
            SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
            break;
    }
}
```

---

### 3. Service Controller Mode

Adds service lifecycle management to a CLI command. The user can run commands like `myapp-server service install`, `myapp-server service start`, `myapp-server service stop`, etc. All service registration, status checking, and lifecycle management is handled by the native launcher -- the Java application does not need to implement any of this.

When the service is started, the launcher runs the Java application with `jdeploy.mode=command`, just like any other CLI command. The Java app simply needs to start running its server/worker logic and stay alive. The launcher handles registering it as a native system service (systemd on Linux, launchd on macOS, Windows Service Manager), monitoring status, and stopping it.

#### package.json

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My App",
    "commands": {
      "myapp-server": {
        "description": "Web server for My App",
        "args": ["-Dapp.role=server"],
        "implements": ["service_controller"]
      },
      "myapp-worker": {
        "description": "Background job worker",
        "args": ["-Dapp.role=worker"],
        "implements": ["service_controller"]
      }
    }
  }
}
```

#### Java Detection

```java
public static void main(String[] args) {
    String mode = System.getProperty("jdeploy.mode", "gui");

    if ("command".equals(mode)) {
        String role = System.getProperty("app.role", "");

        // When running as a service, the launcher handles all lifecycle
        // (install/start/stop/status/uninstall). Your app just needs to run.
        if ("server".equals(role)) {
            startWebServer(Arrays.asList(args));
        } else if ("worker".equals(role)) {
            startWorker(Arrays.asList(args));
        }
    } else {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
```

#### Example User Invocation

```bash
myapp-server service install    # Register with system service manager (handled by launcher)
myapp-server service start      # Start the service (handled by launcher)
myapp-server service status     # Check if running (handled by launcher)
myapp-server service stop       # Stop the service (handled by launcher)
myapp-server service uninstall  # Unregister from service manager (handled by launcher)

myapp-server --port=9090        # Run directly as a CLI command
```

---

### 4. Background Helper (System Tray)

The Background Helper is a system tray application that provides service management and uninstallation from the tray icon. It is NOT a mode your Java app runs in directly -- it is a separate process managed by the jDeploy installer infrastructure.

The Helper:
- Shows a system tray icon with menus to start/stop/view logs for services
- Provides an "Uninstall" option from the tray
- Supports custom "helper actions" -- quick-launch links to URLs, files, or custom protocols
- Manages its own lifecycle with lock files and graceful shutdown signals

Helper actions are configured in `jdeploy.helper.actions` and appear as menu items in the system tray. They let users quickly access dashboards, configuration files, or custom protocol URLs.

#### package.json

Basic example with web URLs and file paths:

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My App",
    "commands": {
      "myapp-server": {
        "description": "Web server",
        "implements": ["service_controller"]
      }
    },
    "helper": {
      "actions": [
        {
          "label": "Dashboard",
          "description": "Open the web dashboard",
          "url": "http://localhost:8080/dashboard"
        },
        {
          "label": "Config File",
          "description": "Open the configuration file",
          "url": "/path/to/config.json"
        }
      ]
    }
  }
}
```

Helper actions can also use custom URL schemes to deep-link into the GUI app. This requires registering the scheme via `urlSchemes` in the jdeploy config:

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My App",
    "urlSchemes": ["myapp"],
    "commands": {
      "myapp-server": {
        "description": "Web server",
        "implements": ["service_controller"]
      }
    },
    "helper": {
      "actions": [
        {
          "label": "Dashboard",
          "description": "Open the web dashboard",
          "url": "http://localhost:8080/dashboard"
        },
        {
          "label": "Settings",
          "description": "Open app settings in the GUI",
          "url": "myapp://settings"
        }
      ]
    }
  }
}
```

**Tip:** When using custom URL schemes, consider enabling `"singleton": true` in your jdeploy config. In singleton mode, if the app is already running when a `myapp://` link is clicked, the launcher activates the existing window and forwards the URI to it via IPC. Without singleton mode, each link click would launch a new instance. Singleton mode applies on Windows and Linux; macOS handles single-instance natively through its Desktop API.

To handle incoming files and URIs in singleton mode, add the **jdeploy-desktop-lib** dependency and register a `JDeployOpenHandler`:

**Swing** (`ca.weblite:jdeploy-desktop-lib-swing:1.0.2`):
```java
JDeploySwingApp.setOpenHandler(new JDeployOpenHandler() {
    public void openFiles(List<File> files) { /* handle files */ }
    public void openURIs(List<URI> uris)   { /* handle URIs */ }
    public void appActivated()             { /* bring window to front */ }
});
```

**JavaFX** (`ca.weblite:jdeploy-desktop-lib-javafx:1.0.2`):
```java
JDeployFXApp.setOpenHandler(new JDeployOpenHandler() {
    public void openFiles(List<File> files) { /* handle files */ }
    public void openURIs(List<URI> uris)   { /* handle URIs */ }
    public void appActivated()             { /* bring window to front */ }
});
```

Without this library, your app will not receive forwarded URIs or file-open events from subsequent launches.

#### Java Detection

The background helper is not a mode your application code runs in. It is a separate system tray process managed by the jDeploy installer. Your app does not need to detect or interact with it directly. The helper interacts with your services via the `service` CLI subcommand interface described above.

---

### 5. Updater Mode

Adds self-update capability to a CLI command. When the user runs `<command> update` (with exactly one argument), the wrapper script triggers the jDeploy auto-update mechanism entirely within the native launcher -- your Java code is never invoked.

Any other invocation (no args, or more than one arg, or the arg is not "update") runs the command normally.

#### package.json

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My App",
    "commands": {
      "myapp-cli": {
        "description": "Run My App CLI",
        "implements": ["updater"]
      },
      "myappctl": {
        "description": "Control My App services",
        "implements": ["service_controller", "updater"]
      }
    }
  }
}
```

#### Example User Invocation

```bash
myapp-cli update            # Triggers auto-update (handled by launcher)
myapp-cli                   # Normal CLI command
myapp-cli foo bar           # Normal CLI command with arguments
myapp-cli update extra      # Normal CLI (2 args, not just "update")
```

#### Java Detection

The update is handled entirely by the native launcher. The launcher runs its built-in update routine before your Java `main()` is ever called. Your app does not need to handle this.

However, your app can check `System.getProperty("jdeploy.updatesAvailable")` to know if updates are available and prompt the user.

---

### 6. Launcher Mode (GUI via CLI)

Provides a CLI shortcut that opens the GUI application. Unlike CLI commands, this launches the desktop app directly with `jdeploy.mode=gui`. Arguments are passed through and treated as files or URLs to open.

On macOS, this uses `open -a MyApp.app` to launch properly through the macOS app bundle mechanism. On Windows/Linux, it calls the app binary directly.

#### package.json

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My App",
    "commands": {
      "myapp": {
        "description": "Open files in My App",
        "implements": ["launcher"]
      }
    }
  }
}
```

#### Example User Invocation

```bash
myapp document.txt           # macOS: open -a "/path/to/My App.app" document.txt
myapp https://example.com    # Opens URL in the GUI app
```

#### Java Detection

No special detection needed. `jdeploy.mode` is `gui` since this launches the full desktop app. File/URL arguments arrive in `main(String[] args)` as normal.

---

### 7. MCP Server Mode (AI Tool Integration)

Registers the application as a Model Context Protocol (MCP) server that AI tools (Claude Desktop, Claude Code, VS Code Copilot, Cursor, etc.) can discover and use. The MCP server is backed by a CLI command defined in `jdeploy.commands`, so `jdeploy.mode` is `command` when running as an MCP server.

During installation, jDeploy configures the MCP server in the user's AI tool configurations (e.g., Claude Desktop's `claude_desktop_config.json`, VS Code's `settings.json`).

Supported AI tools and their capabilities:

| Tool | MCP | Skills | Agents | Auto-Install |
|------|-----|--------|--------|--------------|
| Claude Desktop | Yes | No | No | Yes |
| Claude Code | Yes | Yes | Yes | Yes |
| Codex CLI | Yes | Yes | No | Yes |
| VS Code (Copilot) | Yes | No | No | Yes |
| Cursor | Yes | No | No | Yes |
| Windsurf | Yes | No | No | Yes |
| Gemini CLI | Yes | No | No | Yes |
| OpenCode | Yes | No | No | Yes |
| Warp | Yes | No | No | No |
| JetBrains IDEs | Yes | No | No | No |

#### package.json

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-app.jar",
    "title": "My App",
    "commands": {
      "myapp-mcp": {
        "description": "MCP server for AI integration",
        "args": ["--mcp-mode"]
      }
    },
    "ai": {
      "mcp": {
        "command": "myapp-mcp",
        "args": ["--verbose"],
        "defaultEnabled": true
      }
    }
  }
}
```

The `ai.mcp.command` must reference a command name defined in `jdeploy.commands`. The `args` in the `ai.mcp` section are additional arguments passed when the AI tool invokes the MCP server. `defaultEnabled` controls whether the MCP server is auto-enabled during installation.

#### Java Detection

```java
public static void main(String[] args) {
    String mode = System.getProperty("jdeploy.mode", "gui");

    if ("command".equals(mode)) {
        // Check if running as MCP server via app-specific args.
        // The launcher injects args from jdeploy.commands[].args and jdeploy.ai.mcp.args
        // into the Java args before any user-supplied arguments.
        if (Arrays.asList(args).contains("--mcp-mode")) {
            // MCP server mode: communicate via stdin/stdout using MCP protocol
            startMcpServer();
        } else {
            // args contains only user-supplied arguments (launcher flags stripped)
            runCli(Arrays.asList(args));
        }
    } else {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
```

The MCP server communicates via stdin/stdout using the Model Context Protocol. The launcher injects the static `args` from both the command spec and the `ai.mcp` section before any user-supplied arguments.

#### AI Skills and Agents

Beyond MCP servers, jDeploy v6 also supports installing Claude Code skills and agents:

- **Skills**: Stored in `jdeploy-bundle/ai/skills/` and installed to the user's Claude Code configuration
- **Agents**: Stored in `jdeploy-bundle/ai/agents/` and installed similarly

These are discovered during installation from the `jdeploy-bundle/ai/` directory.

---

## Complete Multi-Mode Example

A single package deploying all modes:

```json
{
  "name": "my-multi-tool",
  "version": "2.0.0",
  "bin": {
    "my-tool": "jdeploy-bundle/jdeploy.js"
  },
  "jdeploy": {
    "javaVersion": "21",
    "jar": "target/my-tool.jar",
    "title": "My Multi-Tool",
    "commands": {
      "my-tool": {
        "description": "Open files in My Multi-Tool",
        "implements": ["launcher"]
      },
      "my-tool-cli": {
        "description": "Run in command-line mode",
        "args": ["-Dapp.role=cli"],
        "implements": ["updater"]
      },
      "my-tool-server": {
        "description": "Start the web server",
        "args": ["-Dapp.role=server", "-Dserver.port=8080"],
        "implements": ["service_controller", "updater"]
      },
      "my-tool-worker": {
        "description": "Start the background job worker",
        "args": ["-Dapp.role=worker"],
        "implements": ["service_controller"]
      },
      "my-tool-mcp": {
        "description": "MCP server for AI tools",
        "args": ["-Dapp.role=mcp"]
      }
    },
    "ai": {
      "mcp": {
        "command": "my-tool-mcp",
        "args": ["--stdio"],
        "defaultEnabled": true
      }
    },
    "helper": {
      "actions": [
        {
          "label": "Dashboard",
          "description": "Open the server dashboard",
          "url": "http://localhost:8080"
        },
        {
          "label": "Docs",
          "description": "View online documentation",
          "url": "https://docs.example.com"
        }
      ]
    }
  }
}
```

### Unified Java Entry Point

```java
public class Main {
    public static void main(String[] args) {
        // Check jdeploy.mode to determine how the app was launched.
        // "gui" = desktop app, "command" = CLI command.
        // args contains only user-supplied arguments
        // (plus any static args from jdeploy.commands[].args injected by the launcher).
        String mode = System.getProperty("jdeploy.mode", "gui");

        if ("command".equals(mode)) {
            // CLI command mode -- dispatch by role.
            // args = user-supplied arguments only.
            String role = System.getProperty("app.role", "");
            List<String> userArgs = Arrays.asList(args);

            switch (role) {
                case "cli":
                    runCliMode(userArgs);
                    break;
                case "server":
                    runServerMode(userArgs);
                    break;
                case "worker":
                    runWorkerMode(userArgs);
                    break;
                case "mcp":
                    runMcpServer();
                    break;
                default:
                    System.err.println("Unknown role: " + role);
                    System.exit(1);
            }
        } else {
            // Default GUI mode (also handles "launcher" implementation).
            // args = file paths or URLs to open.
            SwingUtilities.invokeLater(() -> {
                MainWindow window = new MainWindow();
                window.setVisible(true);
                for (String arg : args) {
                    window.openFile(new File(arg));
                }
            });
        }
    }
}
```

---

## Summary

| Mode | `jdeploy.mode` | Config Location | Purpose |
|------|----------------|-----------------|---------|
| **GUI** | `gui` | (default) | Desktop application |
| **CLI Command** | `command` | `jdeploy.commands.<name>` | Command-line tools on PATH |
| **Service Controller** | `command` | `implements: ["service_controller"]` | Manage background daemons |
| **Background Helper** | N/A | `jdeploy.helper.actions` | System tray management UI |
| **Updater** | N/A | `implements: ["updater"]` | Self-update via CLI (handled by launcher) |
| **Launcher** | `gui` | `implements: ["launcher"]` | CLI shortcut to open GUI |
| **MCP Server** | `command` | `jdeploy.ai.mcp` | AI tool integration |

## Constraints

- **Branch installations** do not support CLI commands, services, or MCP servers. Only GUI mode works for branch installs.
- **Command names** must match `^[A-Za-z0-9][A-Za-z0-9._-]*$` (max 255 chars).
- **Command args** cannot contain shell metacharacters (`;|&`$()`).
- The `launcher` implementation is mutually exclusive with other implementations in practice (it launches in `gui` mode, not `command` mode).
- `updater` and `service_controller` can be combined on the same command.