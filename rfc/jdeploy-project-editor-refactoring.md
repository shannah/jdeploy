# RFC: Refactoring JDeployProjectEditor - Breaking Down a God Object

**RFC Number:** JDEPLOY-2024-001  
**Author:** Martin Fowler (style)  
**Date:** 2024  
**Status:** Proposed  
**Priority:** High  
**Target Release:** Next Major Version

---

## Executive Summary

The `JDeployProjectEditor` class has grown into a 2600+ line "God Object" that violates the Single Responsibility Principle by managing UI construction, file system monitoring, publishing orchestration, and business logic validation simultaneously. This RFC proposes an incremental, nine-phase refactoring strategy to decompose this monolith into focused, testable components while maintaining backward compatibility and following existing panel-based patterns already established in the codebase.

**Expected Outcome:** Reduce `JDeployProjectEditor` to ~800 lines of orchestration code, with extracted responsibilities living in dedicated, reusable components.

---

## Problem Statement

### The Symptom

The `JDeployProjectEditor` class is difficult to understand, test, and modify. Adding a feature to one tab requires understanding the entire file. Bugs in one feature can cascade through shared state management. New team members spend days just understanding the flow.

### Root Causes

1. **Responsibility Overload**: The class simultaneously handles:
   - Construction and lifecycle of 10+ Swing panels
   - File system watching and change detection
   - State mutation and MD5 checksum tracking
   - Publishing workflow orchestration
   - JAR validation and project building
   - Menu bar construction
   - Error handling and user dialogs

2. **Tight Coupling**: UI construction code is intertwined with business logic:
   - Publishing logic lives in `handlePublish0()` but depends on UI state from `mainFields`
   - File watching is entangled with UI refresh logic
   - Validation happens during save but also during publish

3. **Poor Testability**: 
   - Cannot unit-test publishing logic without instantiating entire Swing UI
   - File watching logic cannot be tested in isolation
   - No clear contracts between components

4. **Difficult Evolution**: Each new panel (Permissions, Bundle Filters, Download Page) requires wrestling with the monolithic class structure, despite the existing `DetailsPanel` pattern showing a better way.

### Evidence

- **Lines of Code**: 2600+ lines in a single class
- **Cyclomatic Complexity**: Methods like `handlePublish0()` and `initMainFields()` exceed healthy complexity thresholds
- **Method Count**: 50+ methods, many tightly coupled
- **Tab Count**: 10+ tabs managed inline rather than delegated
- **State Tracking**: 8+ fields tracking checksums, watch service, processing flags

---

## Current State Analysis

### Responsibility Breakdown

| Responsibility | Lines | Methods | Extractable? |
|---|---|---|---|
| Splash Screen UI & Logic | ~150 | 3 | ✓ Yes |
| File Type Associations | ~200 | 4 | ✓ Yes |
| URL Schemes | ~80 | 1 | ✓ Yes |
| CLI Tab | ~100 | 1 | ✓ Yes |
| Runtime Arguments | ~200 | 1 | ✓ Yes |
| File System Watching | ~100 | 3 | ✓ Yes |
| Publishing Orchestration | ~350 | 6 | ✓ Yes (Partial) |
| Menu Construction | ~200 | 8 | ✓ Yes |
| State Management | ~100 | 4 | ✓ Yes (Refactor) |
| Tab Lifecycle & Glue | ~500 | ~15 | ✓ Yes (Orchestration) |

### Existing Patterns to Leverage

The codebase *already contains* the right pattern in:
- `DetailsPanel` - self-contained, uses `getRoot()` for embedding
- `PermissionsPanel` - includes `loadPermissions()`, `savePermissions()`, `addChangeListener()`
- `BundleFiltersPanel` - includes `loadConfiguration()`, `saveConfiguration()`, callbacks
- `PublishSettingsPanel` - focused, single responsibility
- `CheerpJSettings` - form-based with getters/setters

**Key Pattern**: Panels are self-constructing (`initializeUI()`), self-managing (load/save config), and notify via listeners.

---

## Proposed Solution

### Design Principles

1. **Extract by Responsibility**: Each panel becomes responsible for its own UI, state, and business logic
2. **Uniform Interface**: All panels follow the pattern: `getRoot()`, `load(JSONObject)`, `save(JSONObject)`, `addChangeListener()`
3. **Dependency Injection**: Panels receive dependencies (frame, context, services) via constructor
4. **Events Over Coupling**: Panels emit change events rather than modifying shared state
5. **Incremental Extraction**: Phase-by-phase, each independently deployable and testable

### Architecture After Refactoring

```
JDeployProjectEditor (Core Orchestrator, ~800 lines)
├── Delegates UI Construction to
│   ├── DetailsPanel (existing)
│   ├── SplashScreensPanel (new)
│   ├── FiletypesPanel (new)
│   ├── UrlSchemesPanel (new)
│   ├── CliSettingsPanel (new)
│   ├── RuntimeArgsPanel (new)
│   ├── PermissionsPanel (existing)
│   ├── BundleFiltersPanel (existing)
│   ├── CheerpJSettings (existing)
│   ├── DownloadPageSettingsPanel (existing)
│   └── PublishSettingsPanel (existing)
│
├── Delegates File Watching to
│   └── ProjectFileWatcher
│
├── Delegates Menu Construction to
│   └── MenuBarBuilder
│
└── Delegates Publishing to
    └── PublishingCoordinator
```

---

## Detailed Design

### Phase 1: Extract `ProjectFileWatcher` (Separates Concerns)

**Goal**: Isolate file system monitoring from UI lifecycle.

**Current Issues**:
- Watch service logic mixed with EDT event handling
- MD5 tracking spread across multiple fields and methods
- Change handling deeply coupled to reload UI

**New Class**: `ProjectFileWatcher` (injectable, testable)

```java
public class ProjectFileWatcher {
    private final File projectDirectory;
    private final Consumer<FileChangeEvent> onChange;
    private WatchService watchService;
    private boolean watching = true;
    
    // Constructor accepts callbacks
    public ProjectFileWatcher(File projectDirectory, Consumer<FileChangeEvent> onChange)
    
    // Returns record with file, oldHash, newHash
    public static record FileChangeEvent(String filename, String oldHash, String newHash) {}
    
    public void startWatching() throws IOException, InterruptedException
    public void stopWatching()
    private void updateChecksums(File directory)
}
```

**Benefits**:
- `JDeployProjectEditor` no longer manages `watchService` or poll flags
- File watching can be unit-tested independently
- Can add file watching to other screens without duplication

### Phase 2: Extract `SplashScreensPanel` (High-Value, Low-Risk)

**Goal**: Move splash screen UI from `mainFields` into dedicated panel.

**Current Code**: Lines ~800-900, methods `initMainFields()` (splash sections), image loading/selection

**Pattern**: Follow `DetailsPanel` design

```java
public class SplashScreensPanel extends JPanel {
    private JButton splash;
    private JButton installSplash;
    private final File projectDirectory;
    
    public SplashScreensPanel(File projectDirectory)
    public JPanel getRoot()
    public void loadFromPackageJson(JSONObject packageJson)
    public void saveToPackageJson(JSONObject packageJson)
    public void addChangeListener(ActionListener listener)
}
```

**Integration**: 
```java
JPanel splashTab = new SplashScreensPanel(projectDirectory).getRoot();
tabs.add("Splash Screens", splashTab);
```

**Rationale**: Splash screen logic is completely self-contained with no dependencies on other tabs.

### Phase 3: Extract `FiletypesPanel` (Document Type Associations)

**Goal**: Extract file type association UI and directory association UI into unified panel.

**Current Code**: `createDocTypeRow()`, `initDoctypeFields()`, `createDirectoryAssociationPanel()` (~400 lines)

```java
public class FiletypesPanel extends JPanel {
    private final JSONArray documentTypes;
    private final JPanel fileTypesContainer;
    private final DirectoryAssociationFields dirAssoc;
    
    public FiletypesPanel(JSONObject jdeploy)
    public JPanel getRoot()
    public void load(JSONObject jdeploy)
    public void save(JSONObject jdeploy)
    public void addChangeListener(ActionListener listener)
}
```

**Benefits**:
- Directory association and file type association grouped logically
- Can validate both together
- Much cleaner `initMainFields()` method

### Phase 4: Extract `UrlSchemesPanel`

**Goal**: Isolate URL schemes configuration.

**Current Code**: ~80 lines in `initMainFields()`

```java
public class UrlSchemesPanel extends JPanel {
    private JTextField urlSchemes;
    
    public UrlSchemesPanel()
    public JPanel getRoot()
    public void load(JSONObject packageJson)
    public void save(JSONObject packageJson)
    public void addChangeListener(ActionListener listener)
}
```

### Phase 5: Extract `CliSettingsPanel`

**Goal**: Move CLI command configuration to dedicated panel.

**Current Code**: Lines ~1600-1650, command field, tutorial button

```java
public class CliSettingsPanel extends JPanel {
    private JTextField command;
    
    public CliSettingsPanel(JDeployProjectEditorContext context)
    public JPanel getRoot()
    public void load(JSONObject packageJson)
    public void save(JSONObject packageJson)
    public void addChangeListener(ActionListener listener)
}
```

### Phase 6: Extract `RuntimeArgsPanel`

**Goal**: Isolate runtime arguments configuration.

**Current Code**: Lines ~1500-1550, comprehensive ~120 lines

```java
public class RuntimeArgsPanel extends JPanel {
    private JTextArea runArgs;
    
    public RuntimeArgsPanel()
    public JPanel getRoot()
    public void load(JSONObject jdeploy)
    public void save(JSONObject jdeploy)
    public void addChangeListener(ActionListener listener)
}
```

### Phase 7: Extract `MenuBarBuilder`

**Goal**: Relocate menu construction logic.

**Current Code**: `initMenu()`, ~200 lines

```java
public class MenuBarBuilder {
    private final JFrame frame;
    private final File packageJsonFile;
    private final JSONObject packageJson;
    private final JDeployProjectEditorContext context;
    
    public MenuBarBuilder(JFrame frame, File packageJsonFile, 
                         JSONObject packageJson, JDeployProjectEditorContext context)
    
    public JMenuBar build()
    
    private JMenu createFileMenu()
    private JMenu createHelpMenu()
}
```

**Benefits**: Menu logic no longer clutters editor class; easier to test and maintain.

### Phase 8: Extract `PublishingCoordinator`

**Goal**: Separate publishing workflow from UI orchestration.

**Current Issues**: `handlePublish0()` is 300+ lines of validation, coordination, and state mutation

**New Class**: 
```java
public class PublishingCoordinator {
    private final File projectDirectory;
    private final JSONObject packageJson;
    private final ProjectBuilderService projectBuilderService;
    private final PackagingPreferencesService preferencesService;
    private final PublishTargetServiceInterface publishTargetService;
    private final JDeploy jdeploy;
    
    public PublishingCoordinator(File projectDirectory, JSONObject packageJson, ...)
    
    // Returns validation errors as Result type
    public ValidationResult validate()
    
    // Executes publish, returns stream of progress messages
    public OutputStream publish(Consumer<PublishProgress> progressCallback)
    
    public static record PublishProgress(String message, int percentComplete, boolean isError) {}
}
```

**Benefits**:
- Publishing logic testable without Swing
- Progress reporting decoupled from UI
- Reusable in CLI, build scripts, plugins

### Phase 9: Refactor `JDeployProjectEditor` as Orchestrator

**Goal**: Leave only tab lifecycle and frame management.

**Remaining Responsibilities** (~800 lines):
- `initFrame()` - frame setup, watch service initialization
- `initMainFields()` - tab creation and assembly (now delegating to panels)
- `handleSave()` - coordinate panel saves and file writes
- `handleClosing()` - cleanup and validation before exit
- `show()`, `focus()`, etc. - public API
- State management: `modified` flag, file watching

**New `initMainFields()` Pattern**:
```java
private void initMainFields(Container cnt) {
    // Create panels
    DetailsPanel detailsPanel = new DetailsPanel();
    SplashScreensPanel splashPanel = new SplashScreensPanel(projectDirectory);
    FiletypesPanel filetypesPanel = new FiletypesPanel(packageJSON.getJSONObject("jdeploy"));
    UrlSchemesPanel urlSchemesPanel = new UrlSchemesPanel();
    CliSettingsPanel cliPanel = new CliSettingsPanel(context);
    RuntimeArgsPanel runtimeArgsPanel = new RuntimeArgsPanel();
    PermissionsPanel permissionsPanel = new PermissionsPanel();
    BundleFiltersPanel bundlePanel = new BundleFiltersPanel(projectDirectory);
    
    // Load configuration
    detailsPanel.load(packageJSON);
    splashPanel.load(packageJSON.getJSONObject("jdeploy"));
    filetypesPanel.load(packageJSON.getJSONObject("jdeploy"));
    // ... etc
    
    // Wire change listeners
    detailsPanel.addChangeListener(evt -> setModified());
    splashPanel.addChangeListener(evt -> setModified());
    // ... etc
    
    // Assemble tabs
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Details", detailsPanel.getRoot());
    tabs.addTab("Splash Screens", splashPanel.getRoot());
    tabs.addTab("Filetypes", filetypesPanel.getRoot());
    // ... etc
    
    // Add to container
    cnt.add(tabs, BorderLayout.CENTER);
    cnt.add(createBottomButtons(), BorderLayout.SOUTH);
}
```

---

## Implementation Strategy

### Phasing Approach

Each phase is independently deployable and immediately testable:

| Phase | Scope | Complexity | Risk | Effort | Duration |
|-------|-------|-----------|------|--------|----------|
| 1 | ProjectFileWatcher | Medium | Low | 2 days | Week 1 |
| 2 | SplashScreensPanel | Low | Low | 1 day | Week 1 |
| 3 | FiletypesPanel | Medium | Low | 2 days | Week 2 |
| 4 | UrlSchemesPanel | Low | Low | 4 hours | Week 2 |
| 5 | CliSettingsPanel | Low | Low | 4 hours | Week 2 |
| 6 | RuntimeArgsPanel | Low | Low | 4 hours | Week 2 |
| 7 | MenuBarBuilder | Medium | Low | 1 day | Week 3 |
| 8 | PublishingCoordinator | High | Medium | 3 days | Week 3-4 |
| 9 | Final Refactor | Low | Low | 1 day | Week 4 |

### Workflow for Each Phase

1. **Create new panel class** with full constructor, load/save, listeners
2. **Extract code** from `JDeployProjectEditor` into new class
3. **Update `initMainFields()`** to instantiate and use new panel
4. **Run integration tests** - ensure tab functions identically
5. **Update unit tests** - test new panel in isolation
6. **Code review** before merging
7. **Deploy** with feature flag if necessary (CheerpJ example already in code)

### Backward Compatibility

- All changes are **internal refactoring** - no public API changes
- File format (`package.json`) unchanged
- UI behavior unchanged
- Existing code calling `JDeployProjectEditor` unchanged

---

## Testing Strategy

### Unit Testing (New)

Each extracted panel is independently testable:

```java
public class SplashScreensPanelTest {
    private SplashScreensPanel panel;
    private JSONObject testConfig;
    
    @BeforeEach
    void setup() {
        testConfig = new JSONObject();
        panel = new SplashScreensPanel(tempDirectory);
    }
    
    @Test
    void testLoadSetsUiFromJson()
    @Test
    void testSaveWritesUiStateToJson()
    @Test
    void testChangeListenerFires()
}
```

### Integration Testing (Enhanced)

Existing integration tests gain clarity:

```java
public class JDeployProjectEditorIntegrationTest {
    private JDeployProjectEditor editor;
    
    @Test
    void testPublishingFlow() {
        // Now easier to understand - panels loaded, then publish triggered
        editor.showPublishDialog();
        // Test publish without Swing entanglement
    }
}
```

### Regression Testing

Before each phase merge:
1. Run full existing test suite
2. Manually test affected tab in GUI
3. Verify file save/load round-trip
4. Verify change listeners fire

### Test Coverage Goals

| Component | Current | Target |
|-----------|---------|--------|
| JDeployProjectEditor | ~30% | ~60% |
| Panel classes | ~10% | ~80% |
| PublishingCoordinator | N/A | ~90% |
| ProjectFileWatcher | N/A | ~85% |

---

## Risks and Mitigations

### Risk 1: Breaking File Save Logic

**Severity**: High  
**Probability**: Medium

**Mitigation**:
- Create `ConfigurationSerializer` utility for consistent save patterns
- All panels save through same interface
- Round-trip test: load → modify → save → reload → verify

### Risk 2: Lost Functionality During Extraction

**Severity**: Medium  
**Probability**: Low

**Mitigation**:
- Keep original code alongside new code during transition
- Feature parity tests before deletion
- Code review checklist for each phase

### Risk 3: Change Listener Chain Failures

**Severity**: Medium  
**Probability**: Low

**Mitigation**:
- Use event queue to prevent listener exceptions from cascading
- Test each listener independently
- Document listener contract clearly

### Risk 4: File Watching Race Conditions

**Severity**: Medium  
**Probability**: Medium

**Mitigation**:
- `ProjectFileWatcher` uses concurrent data structures
- Decouple file detection from UI refresh
- Add integration tests for rapid file changes

### Risk 5: Team Knowledge Loss

**Severity**: Low  
**Probability**: Low

**Mitigation**:
- Document each phase with clear before/after
- Code review by at least 2 team members per phase
- Pair programming for complex phases

---

## Success Criteria

### Quantitative Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| JDeployProjectEditor LOC | 2600 | <800 | Line count |
| Cyclomatic Complexity (max method) | 45+ | <15 | SonarQube |
| Panel Classes | 5 | 10 | Count |
| Unit Test Coverage | ~30% | >70% | JaCoCo |
| Average Method Length | 80 lines | <30 lines | Analysis |
| Public Method Count | 50+ | <20 | Count |

### Qualitative Metrics

1. **Code Comprehension**: New developer can understand one tab in <1 hour (currently 1 day)
2. **Feature Addition Time**: Adding a new tab takes <2 hours (currently 4+ hours wrestling with monolith)
3. **Bug Localization**: Bugs clearly in one component; no "what else might this affect?" uncertainty
4. **Testability**: Can write unit tests without Swing/headless testing setup
5. **Documentation**: Each panel class includes javadoc; usage patterns clear

### Definition of "Done" for Refactoring

- [ ] All phases completed and merged
- [ ] Test coverage >70% overall, >80% for extracted components
- [ ] No regression in GUI functionality
- [ ] JDeployProjectEditor <800 lines
- [ ] All methods <30 lines average
- [ ] Design document updated
- [ ] Team documentation updated

---

## Appendix A: Refactoring Patterns Applied

### Pattern: Extract Class
Moving responsibilities from `JDeployProjectEditor` to `SplashScreensPanel`, `MenuBarBuilder`, etc.

### Pattern: Move Method
Methods like `initDoctypeFields()` → `FiletypesPanel.createPermissionRow()`

### Pattern: Replace Callback with Observer
Change listeners instead of direct field mutation

### Pattern: Separate Concerns
File watching (I/O) separate from UI (Swing), separate from business logic (publishing)

### Pattern: Introduce Parameter Object
`ProjectFileWatcher.FileChangeEvent` encapsulates file change data

### Pattern: Extract Method Object
`PublishingCoordinator` encapsulates complex publishing workflow

---

## Appendix B: References and Further Reading

- **Fowler, M.** (2018). *Refactoring: Improving the Design of Existing Code* (2nd ed.). Addison-Wesley. Patterns: Extract Class, Move Method, Replace Callback with Observer.

- **Martin, R. C.** (2008). *Clean Code: A Handbook of Agile Software Craftsmanship*. Prentice Hall. Chapter 10: Classes.

- **Ousterhout, J.** (2018). *A Philosophy of Software Design*. Yaknyam Press. Principle: "Complexity comes from an accumulation of dependencies and obscurities."

---

## Appendix C: Example: Extracted `SplashScreensPanel` Skeleton

```java
package ca.weblite.jdeploy.gui.tabs;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import org.json.JSONObject;

/**
 * Panel for managing splash screen images (splash.png and installsplash.png).
 * Self-contained UI construction, load/save, and change notification.
 */
public class SplashScreensPanel extends JPanel {
    private JButton splashButton;
    private JButton installSplashButton;
    private final File projectDirectory;
    private ActionListener changeListener;

    public SplashScreensPanel(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BorderLayout());
        
        JPanel contentPanel = createContentPanel();
        JPanel helpPanel = createHelpPanel();
        
        add(helpPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel createContentPanel() {
        // Build UI for splash screen selection
        // Return panel with splashButton and installSplashButton
    }

    private JPanel createHelpPanel() {
        // Return help button panel
    }

    public JPanel getRoot() {
        return this;
    }

    public void load(JSONObject jdeploy) {
        // Update UI from jdeploy object
    }

    public void save(JSONObject jdeploy) {
        // Write current UI state to jdeploy object
    }

    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }
}
```

---

## Summary

The refactoring of `JDeployProjectEditor` is a **necessary, achievable, and low-risk** investment in code health. By following established patterns already in the codebase and decomposing the monolith into focused, testable components, we will:

- **Reduce complexity** from 2600+ lines to 800 lines of orchestration
- **Improve testability** by enabling unit tests without Swing setup
- **Accelerate development** by making features self-contained
- **Reduce bugs** through clearer responsibilities and contracts

The incremental, phased approach ensures each step is immediately valuable and that we can course-correct based on real experience rather than theory.

**Recommendation**: Approve and begin Phase 1 (ProjectFileWatcher extraction) in the current sprint.
