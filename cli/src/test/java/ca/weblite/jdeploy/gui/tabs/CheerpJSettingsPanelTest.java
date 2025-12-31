package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheerpJSettingsPanel")
public class CheerpJSettingsPanelTest implements ActionListener {
    private CheerpJSettingsPanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() {
        panel = new CheerpJSettingsPanel();
        changeListenerCallCount = new AtomicInteger(0);
        panel.addChangeListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        changeListenerCallCount.incrementAndGet();
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
        assertEquals(panel, panel.getRoot());
    }

    @Test
    @DisplayName("Should load cheerpj when enabled")
    void testLoadEnabledCheerpj() {
        JSONObject jdeploy = new JSONObject();
        JSONObject cheerpj = new JSONObject();
        cheerpj.put("enabled", true);
        JSONObject githubPages = new JSONObject();
        githubPages.put("branch", "gh-pages");
        githubPages.put("branchPath", "/dist");
        githubPages.put("tagPath", "/releases");
        githubPages.put("path", "/public");
        cheerpj.put("githubPages", githubPages);
        jdeploy.put("cheerpj", cheerpj);

        panel.load(jdeploy);

        assertTrue(panel.getCheerpJSettings().getEnableCheerpJ().isSelected());
        assertEquals("gh-pages", panel.getCheerpJSettings().getGithubPagesBranch().getText());
        assertEquals("/dist", panel.getCheerpJSettings().getGithubPagesBranchPath().getText());
        assertEquals("/releases", panel.getCheerpJSettings().getGithubPagesTagPath().getText());
        assertEquals("/public", panel.getCheerpJSettings().getGithubPagesPath().getText());
    }

    @Test
    @DisplayName("Should load cheerpj when disabled")
    void testLoadDisabledCheerpj() {
        JSONObject jdeploy = new JSONObject();
        JSONObject cheerpj = new JSONObject();
        cheerpj.put("enabled", false);
        JSONObject githubPages = new JSONObject();
        githubPages.put("branch", "main");
        cheerpj.put("githubPages", githubPages);
        jdeploy.put("cheerpj", cheerpj);

        panel.load(jdeploy);

        assertFalse(panel.getCheerpJSettings().getEnableCheerpJ().isSelected());
        assertEquals("main", panel.getCheerpJSettings().getGithubPagesBranch().getText());
    }

    @Test
    @DisplayName("Should load gracefully when cheerpj object is missing")
    void testLoadMissingCheerpj() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("other", "value");

        panel.load(jdeploy);

        assertFalse(panel.getCheerpJSettings().getEnableCheerpJ().isSelected());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesBranch().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesBranchPath().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesTagPath().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesPath().getText());
    }

    @Test
    @DisplayName("Should load gracefully when jdeploy is null")
    void testLoadNullJdeploy() {
        panel.load(null);

        assertFalse(panel.getCheerpJSettings().getEnableCheerpJ().isSelected());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesBranch().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesBranchPath().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesTagPath().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesPath().getText());
    }

    @Test
    @DisplayName("Should load partial githubPages settings")
    void testLoadPartialGithubPages() {
        JSONObject jdeploy = new JSONObject();
        JSONObject cheerpj = new JSONObject();
        cheerpj.put("enabled", true);
        JSONObject githubPages = new JSONObject();
        githubPages.put("branch", "gh-pages");
        githubPages.put("path", "/output");
        cheerpj.put("githubPages", githubPages);
        jdeploy.put("cheerpj", cheerpj);

        panel.load(jdeploy);

        assertTrue(panel.getCheerpJSettings().getEnableCheerpJ().isSelected());
        assertEquals("gh-pages", panel.getCheerpJSettings().getGithubPagesBranch().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesBranchPath().getText());
        assertEquals("", panel.getCheerpJSettings().getGithubPagesTagPath().getText());
        assertEquals("/output", panel.getCheerpJSettings().getGithubPagesPath().getText());
    }

    @Test
    @DisplayName("Should save all fields when populated")
    void testSaveAllFields() {
        JSONObject jdeploy = new JSONObject();

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("gh-pages");
        panel.getCheerpJSettings().getGithubPagesBranchPath().setText("/dist");
        panel.getCheerpJSettings().getGithubPagesTagPath().setText("/releases");
        panel.getCheerpJSettings().getGithubPagesPath().setText("/public");

        panel.save(jdeploy);

        assertTrue(jdeploy.has("cheerpj"));
        JSONObject cheerpj = jdeploy.getJSONObject("cheerpj");
        assertTrue(cheerpj.getBoolean("enabled"));
        assertTrue(cheerpj.has("githubPages"));
        JSONObject githubPages = cheerpj.getJSONObject("githubPages");
        assertEquals("gh-pages", githubPages.getString("branch"));
        assertEquals("/dist", githubPages.getString("branchPath"));
        assertEquals("/releases", githubPages.getString("tagPath"));
        assertEquals("/public", githubPages.getString("path"));
    }

    @Test
    @DisplayName("Should save enabled flag only when other fields are empty")
    void testSaveEnabledFlagOnly() {
        JSONObject jdeploy = new JSONObject();

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("");
        panel.getCheerpJSettings().getGithubPagesBranchPath().setText("");
        panel.getCheerpJSettings().getGithubPagesTagPath().setText("");
        panel.getCheerpJSettings().getGithubPagesPath().setText("");

        panel.save(jdeploy);

        assertTrue(jdeploy.has("cheerpj"));
        JSONObject cheerpj = jdeploy.getJSONObject("cheerpj");
        assertTrue(cheerpj.getBoolean("enabled"));
    }

    @Test
    @DisplayName("Should remove cheerpj when disabled and all fields empty")
    void testSaveRemoveWhenDisabledAndEmpty() {
        JSONObject jdeploy = new JSONObject();
        JSONObject cheerpj = new JSONObject();
        cheerpj.put("enabled", true);
        jdeploy.put("cheerpj", cheerpj);
        jdeploy.put("other", "value");

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(false);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("");
        panel.getCheerpJSettings().getGithubPagesBranchPath().setText("");
        panel.getCheerpJSettings().getGithubPagesTagPath().setText("");
        panel.getCheerpJSettings().getGithubPagesPath().setText("");

        panel.save(jdeploy);

        assertFalse(jdeploy.has("cheerpj"));
        assertTrue(jdeploy.has("other"));
    }

    @Test
    @DisplayName("Should create cheerpj structure if missing")
    void testSaveCreatesStructureWhenMissing() {
        JSONObject jdeploy = new JSONObject();
        assertFalse(jdeploy.has("cheerpj"));

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("gh-pages");

        panel.save(jdeploy);

        assertTrue(jdeploy.has("cheerpj"));
        JSONObject cheerpj = jdeploy.getJSONObject("cheerpj");
        assertTrue(cheerpj.has("enabled"));
        assertTrue(cheerpj.has("githubPages"));
    }

    @Test
    @DisplayName("Should handle null jdeploy on save")
    void testSaveNullJdeploy() {
        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("gh-pages");

        // Should not throw
        panel.save(null);
    }

    @Test
    @DisplayName("Should skip empty githubPages fields on save")
    void testSaveSkipsEmptyFields() {
        JSONObject jdeploy = new JSONObject();

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("gh-pages");
        panel.getCheerpJSettings().getGithubPagesBranchPath().setText("");
        panel.getCheerpJSettings().getGithubPagesTagPath().setText("");
        panel.getCheerpJSettings().getGithubPagesPath().setText("");

        panel.save(jdeploy);

        JSONObject cheerpj = jdeploy.getJSONObject("cheerpj");
        JSONObject githubPages = cheerpj.getJSONObject("githubPages");
        assertTrue(githubPages.has("branch"));
        assertFalse(githubPages.has("branchPath"));
        assertFalse(githubPages.has("tagPath"));
        assertFalse(githubPages.has("path"));
    }

    @Test
    @DisplayName("Should trim whitespace from fields on save")
    void testSaveTrimsWhitespace() {
        JSONObject jdeploy = new JSONObject();

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("  gh-pages  ");
        panel.getCheerpJSettings().getGithubPagesBranchPath().setText("  /dist  ");
        panel.getCheerpJSettings().getGithubPagesTagPath().setText("  /releases  ");
        panel.getCheerpJSettings().getGithubPagesPath().setText("  /public  ");

        panel.save(jdeploy);

        JSONObject cheerpj = jdeploy.getJSONObject("cheerpj");
        JSONObject githubPages = cheerpj.getJSONObject("githubPages");
        assertEquals("gh-pages", githubPages.getString("branch"));
        assertEquals("/dist", githubPages.getString("branchPath"));
        assertEquals("/releases", githubPages.getString("tagPath"));
        assertEquals("/public", githubPages.getString("path"));
    }

    @Test
    @DisplayName("Should load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        JSONObject originalJdeploy = new JSONObject();
        JSONObject cheerpj = new JSONObject();
        cheerpj.put("enabled", true);
        JSONObject githubPages = new JSONObject();
        githubPages.put("branch", "gh-pages");
        githubPages.put("branchPath", "/dist");
        githubPages.put("tagPath", "/releases");
        githubPages.put("path", "/public");
        cheerpj.put("githubPages", githubPages);
        originalJdeploy.put("cheerpj", cheerpj);

        // Load
        panel.load(originalJdeploy);

        // Save to new object
        JSONObject newJdeploy = new JSONObject();
        panel.save(newJdeploy);

        // Verify data integrity
        assertTrue(newJdeploy.has("cheerpj"));
        JSONObject newCheerpj = newJdeploy.getJSONObject("cheerpj");
        assertEquals(true, newCheerpj.getBoolean("enabled"));
        JSONObject newGithubPages = newCheerpj.getJSONObject("githubPages");
        assertEquals("gh-pages", newGithubPages.getString("branch"));
        assertEquals("/dist", newGithubPages.getString("branchPath"));
        assertEquals("/releases", newGithubPages.getString("tagPath"));
        assertEquals("/public", newGithubPages.getString("path"));
    }

    @Test
    @DisplayName("Should register and fire change listener on checkbox change")
    void testChangeListenerFiresOnCheckboxChange() {
        int initialCount = changeListenerCallCount.get();
        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        assertTrue(changeListenerCallCount.get() > initialCount);
    }

    @Test
    @DisplayName("Should register and fire change listener on text field changes")
    void testChangeListenerFiresOnTextFieldChange() {
        int initialCount = changeListenerCallCount.get();
        panel.getCheerpJSettings().getGithubPagesBranch().setText("gh-pages");
        assertTrue(changeListenerCallCount.get() > initialCount);
    }

    @Test
    @DisplayName("Should fire listener on branch field change")
    void testChangeListenerBranchField() {
        int initialCount = changeListenerCallCount.get();
        panel.getCheerpJSettings().getGithubPagesBranch().setText("main");
        assertTrue(changeListenerCallCount.get() > initialCount);
    }

    @Test
    @DisplayName("Should fire listener on branchPath field change")
    void testChangeListenerBranchPathField() {
        int initialCount = changeListenerCallCount.get();
        panel.getCheerpJSettings().getGithubPagesBranchPath().setText("/output");
        assertTrue(changeListenerCallCount.get() > initialCount);
    }

    @Test
    @DisplayName("Should fire listener on tagPath field change")
    void testChangeListenerTagPathField() {
        int initialCount = changeListenerCallCount.get();
        panel.getCheerpJSettings().getGithubPagesTagPath().setText("/dist");
        assertTrue(changeListenerCallCount.get() > initialCount);
    }

    @Test
    @DisplayName("Should fire listener on path field change")
    void testChangeListenerPathField() {
        int initialCount = changeListenerCallCount.get();
        panel.getCheerpJSettings().getGithubPagesPath().setText("/build");
        assertTrue(changeListenerCallCount.get() > initialCount);
    }

    @Test
    @DisplayName("Should handle listener replacement")
    void testListenerCanBeReplaced() {
        AtomicInteger newListenerCount = new AtomicInteger(0);
        panel.addChangeListener(e -> newListenerCount.incrementAndGet());

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        assertTrue(newListenerCount.get() > 0);
    }

    @Test
    @DisplayName("Should save without affecting other jdeploy fields")
    void testSaveDoesNotAffectOtherFields() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("name", "my-app");
        jdeploy.put("version", "1.0.0");
        jdeploy.put("other", "value");

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("gh-pages");

        panel.save(jdeploy);

        assertEquals("my-app", jdeploy.getString("name"));
        assertEquals("1.0.0", jdeploy.getString("version"));
        assertEquals("value", jdeploy.getString("other"));
    }

    @Test
    @DisplayName("Should preserve other fields when updating existing cheerpj")
    void testPreserveOtherFieldsOnUpdate() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("name", "my-app");
        JSONObject cheerpj = new JSONObject();
        cheerpj.put("enabled", false);
        jdeploy.put("cheerpj", cheerpj);

        panel.getCheerpJSettings().getEnableCheerpJ().setSelected(true);
        panel.getCheerpJSettings().getGithubPagesBranch().setText("gh-pages");

        panel.save(jdeploy);

        assertEquals("my-app", jdeploy.getString("name"));
        assertTrue(jdeploy.getJSONObject("cheerpj").getBoolean("enabled"));
    }
}
