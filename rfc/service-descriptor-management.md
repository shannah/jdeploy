# RFC: Service Descriptor Management

**Status:** Implemented
**Date:** 2026-01-03
**Author:** jDeploy Team

## Abstract

This RFC specifies the service descriptor management system for jDeploy applications. Service descriptors track CLI commands that implement the `service_controller` interface, enabling proper lifecycle management during installation, updates, and uninstallation. This specification defines the storage format, file locations, and behavioral contracts that external tools can rely on when interacting with jDeploy-managed services.

## Motivation

CLI commands that implement `service_controller` can register themselves as native system services (via systemd, launchd, Windows Service Manager, etc.). When updating or uninstalling an application, the installer must ensure that:

1. Services are stopped before file updates to prevent file locking issues
2. Services removed in new versions are properly uninstalled
3. Service metadata is preserved across installations for proper cleanup

Without a formal tracking mechanism, the installer cannot reliably manage service lifecycle, potentially leading to orphaned services, failed updates, or incomplete uninstallations.

**Note on Branch Installations:** Branch releases do not support CLI commands or CLI launchers, and therefore do not support service-controller commands. Branch installations are designed for parallel GUI application testing and avoid the PATH conflicts that would arise from multiple CLI command versions.

## Specification

### 1. Service Descriptor File Format

Service descriptors are stored as JSON files with the following schema:

```json
{
  "packageName": "string (required)",
  "version": "string (required)",
  "installedTimestamp": "number (milliseconds since epoch)",
  "lastModified": "number (milliseconds since epoch)",
  "commandSpec": {
    "name": "string (required)",
    "description": "string or null",
    "args": ["string", "..."],
    "implements": ["string", "..."]
  }
}
```

#### Field Definitions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `packageName` | string | Yes | Fully qualified package name (e.g., `com.example.myapp` or `@scope/package`) |
| `version` | string | Yes | Package version (e.g., `1.2.3`) |
| `installedTimestamp` | number | Yes | Unix timestamp (milliseconds) when service was first registered |
| `lastModified` | number | Yes | Unix timestamp (milliseconds) when descriptor was last updated |
| `commandSpec` | object | Yes | Command specification (see below) |

#### Command Specification

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Command name (e.g., `myapp-server`) |
| `description` | string \| null | No | Human-readable description |
| `args` | array of strings | No | Static arguments passed to command |
| `implements` | array of strings | Yes | Must include `"service_controller"` |

**Validation Rules:**
- `commandSpec.implements` MUST contain `"service_controller"`
- `packageName` MUST NOT be empty
- `version` MUST NOT be empty
- `commandSpec.name` MUST match regex: `^[A-Za-z0-9][A-Za-z0-9._-]*$`

### 2. File System Locations

Service descriptors are stored in architecture-specific directories to prevent conflicts between different system architectures:

```
~/.jdeploy/services/{arch}/{fqpn}/{commandName}.json
```

**Note:** Branch installations do not support CLI commands, so branch-specific service descriptor paths do not exist.

#### Path Components

| Component | Description | Example |
|-----------|-------------|---------|
| `{arch}` | System architecture identifier | `x64`, `arm64` |
| `{fqpn}` | Fully qualified package name | `com.example.myapp`, `@scope-package` |
| `{commandName}` | CLI command name | `myapp-server` |

**Architecture Values:**
- `x64` - x86-64 / AMD64 / Intel 64
- `arm64` - ARM64 / AArch64
- `x86` - 32-bit x86 (legacy)

**FQPN Encoding:**
- NPM scoped packages: `@scope/package` → `@scope-package`
- Forward slashes replaced with hyphens
- Special characters may be URL-encoded

### 3. Service Lifecycle

#### 3.1 Registration (Installation)

When a CLI command implementing `service_controller` is installed:

1. **Detect Service Commands**: Parse package.json `jdeploy.commands` section
2. **Filter for Services**: Identify commands with `"implements": ["service_controller"]`
3. **Create Descriptor**: Generate service descriptor JSON
4. **Write to Disk**: Save to architecture-specific path
5. **Log Registration**: Record in installation logs

**Guarantees:**
- Service descriptor MUST be created atomically
- Descriptor MUST be written before service installation
- File permissions MUST allow owner read/write access

#### 3.2 Update Detection

Before updating an application, the installer MUST:

1. **Load Current Descriptors**: Read all service descriptors for package
2. **Compare with New Version**: Compare against new package.json commands
3. **Identify Removed Services**: Find services in current but not in new version
4. **Identify New Services**: Find services in new but not in current version
5. **Identify Unchanged Services**: Find services in both versions

**Update Actions:**

| Scenario | Action |
|----------|--------|
| Service removed | Stop service, unregister descriptor, uninstall service |
| Service added | Register descriptor after installation |
| Service unchanged | Update descriptor with new version and timestamp |

#### 3.3 Service Stopping

Before updating or uninstalling, services MUST be stopped:

**Stop Command Format:**
```bash
# Unix-like systems
{commandName} service stop

# Windows
{commandName}.cmd service stop
```

**Stop Sequence:**
1. Execute stop command for each registered service
2. Wait for command to complete (with timeout)
3. Log exit code and any errors
4. Continue with update/uninstall regardless of stop success

**Error Handling:**
- Non-zero exit codes SHOULD be logged but MUST NOT block update/uninstall
- Timeouts SHOULD be logged but MUST NOT block update/uninstall
- Missing commands SHOULD be logged but MUST NOT block update/uninstall

#### 3.4 Uninstallation

During uninstallation, the installer MUST:

1. **Load Service Descriptors**: Read all descriptors for package
2. **Stop Services**: Execute stop command for each service
3. **Uninstall Services**: Execute uninstall command if supported
4. **Delete Descriptors**: Remove all descriptor JSON files
5. **Clean Directories**: Remove empty service directories

**Uninstall Command Format (Optional):**
```bash
{commandName} service uninstall
```

**Cleanup Rules:**
- Service descriptors MUST be deleted after services are stopped
- Empty package directories SHOULD be removed
- Architecture directories MUST NOT be removed (may contain other packages)

### 4. Branch Installations

**Branch installations do not support CLI commands or CLI launchers.**

Branch releases are designed for parallel GUI application testing and intentionally exclude CLI functionality to avoid:
- PATH conflicts between multiple versions
- Service name collisions
- Ambiguity about which version of a command is executed

Applications installed from branch sources will:
- Install GUI components normally
- Skip CLI command installation
- Skip CLI launcher creation
- Not create any service descriptors

Users who need CLI functionality should install from the main release channel.

### 5. Multi-Service Packages

Packages MAY define multiple service-controller commands:

```json
{
  "jdeploy": {
    "commands": {
      "myapp-web": {
        "implements": ["service_controller"]
      },
      "myapp-worker": {
        "implements": ["service_controller"]
      },
      "myapp-scheduler": {
        "implements": ["service_controller"]
      }
    }
  }
}
```

**Multi-Service Behavior:**
- Each service MUST have its own descriptor file
- All services MUST be stopped before update/uninstall
- Stop order is undefined (services MUST NOT depend on each other)
- Partial registration failures MUST NOT prevent other services from registering

### 6. Platform-Specific Considerations

#### 6.1 Windows

- Command invocation uses `.cmd` extension
- Service descriptors use forward slashes in paths (normalized internally)
- PATH separator is semicolon (handled by installer)

#### 6.2 macOS

- Service descriptors use absolute POSIX paths
- Commands invoked without extension
- User home directory from `$HOME` environment variable

#### 6.3 Linux

- Service descriptors use absolute POSIX paths
- Commands invoked without extension
- User home directory from `$HOME` environment variable
- Desktop environment detection may affect installation flow

### 7. External Tool Integration

External tools can interact with service descriptors by:

#### Reading Service Status

```bash
# List all registered services for a package
ls ~/.jdeploy/services/*/com.example.myapp/*.json

# Read service descriptor
cat ~/.jdeploy/services/x64/com.example.myapp/myapp-server.json
```

#### Querying Service Information

```javascript
// Parse service descriptor
const descriptor = JSON.parse(
  fs.readFileSync('~/.jdeploy/services/x64/com.example.myapp/myapp-server.json')
);

console.log(`Service: ${descriptor.commandSpec.name}`);
console.log(`Version: ${descriptor.version}`);
console.log(`Installed: ${new Date(descriptor.installedTimestamp)}`);
```

#### Monitoring for Changes

Tools can watch the services directory for changes:
- New JSON files indicate service registration
- Deleted JSON files indicate service unregistration
- Modified timestamps indicate descriptor updates

**Warning:** External tools MUST NOT modify service descriptors. Modifications may cause undefined behavior during updates or uninstalls.

### 8. Compatibility and Versioning

#### Backward Compatibility

- Service descriptors added in jDeploy installer 1.0-SNAPSHOT
- Older installations without descriptors will not be tracked
- Installer handles missing descriptors gracefully (no-op)

#### Forward Compatibility

Future versions MAY add additional fields to the descriptor schema. External tools SHOULD ignore unknown fields.

**Reserved Fields:**
- Any field starting with `_` is reserved for future use
- Any field starting with `jdeploy` is reserved for future use

#### Schema Version

Future versions may include a `schemaVersion` field:

```json
{
  "schemaVersion": "1.0",
  "packageName": "...",
  ...
}
```

If absent, version 1.0 is assumed.

### 9. Security Considerations

#### File Permissions

- Service descriptor files MUST be readable/writable by owner only
- Directories SHOULD have `0700` permissions (Unix) or equivalent
- No sensitive information SHOULD be stored in descriptors

#### Command Injection Prevention

- Command names are validated against whitelist regex
- Arguments are validated to prevent shell metacharacters
- Service stop commands are executed without shell interpretation

#### Privilege Escalation

- Service descriptors stored in user home directory (no root required)
- Service operations run with user privileges
- No setuid or sudo operations

### 10. Error Handling and Recovery

#### Missing Descriptors

If service descriptors are missing during update/uninstall:
- Installer SHOULD log warning
- Installer MUST continue with update/uninstall
- No services will be stopped (undefined behavior)

#### Corrupted Descriptors

If JSON parsing fails:
- Descriptor SHOULD be logged as corrupted
- Descriptor SHOULD be ignored (not block operations)
- File MAY be deleted during cleanup

#### Orphaned Descriptors

If a command wrapper is deleted but descriptor remains:
- Service stop will fail (logged)
- Descriptor SHOULD be deleted during uninstall
- No system service will be affected

### 11. Example Workflows

#### Install Package with Service

```
1. User installs: npm install -g @myorg/myapp
2. Installer detects service_controller command: "myapp-server"
3. Installer creates: ~/.jdeploy/services/x64/@myorg-myapp/myapp-server.json
4. User can now run: myapp-server service install
```

#### Update Package (Service Unchanged)

```
1. User updates: npm install -g @myorg/myapp@2.0.0
2. Installer loads: ~/.jdeploy/services/x64/@myorg-myapp/myapp-server.json
3. Installer stops service: myapp-server service stop
4. Installer updates application files
5. Installer updates descriptor with version 2.0.0 and new timestamp
6. User manually restarts: myapp-server service start
```

#### Update Package (Service Removed)

```
1. User updates to version without myapp-server
2. Installer loads: ~/.jdeploy/services/x64/@myorg-myapp/myapp-server.json
3. Installer stops service: myapp-server service stop
4. Installer uninstalls service: myapp-server service uninstall
5. Installer deletes: ~/.jdeploy/services/x64/@myorg-myapp/myapp-server.json
6. Installer updates application files
```

#### Uninstall Package

```
1. User uninstalls: npm uninstall -g @myorg/myapp
2. Installer loads all descriptors from ~/.jdeploy/services/*/@myorg-myapp/
3. For each service:
   - Stop: myapp-server service stop
   - Uninstall: myapp-server service uninstall (optional)
   - Delete descriptor
4. Remove command wrappers
5. Clean up empty directories
```

## Implementation Notes

This specification describes the contract that jDeploy provides. Implementations may vary across platforms but MUST adhere to:

1. File paths and naming conventions
2. JSON schema structure
3. Lifecycle guarantees
4. Error handling behavior

## References

- [CLI Commands in Installer RFC](./cli-commands-in-installer.md) - Service controller specification
- NPM Package Specification
- jDeploy package.json Extensions

## Changelog

### 2026-01-03 - Initial Version
- Service descriptor JSON schema
- File system layout specification
- Lifecycle management specification
- Multi-service and branch isolation
- External tool integration guidelines
