package ca.weblite.jdeploy.gui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import static org.junit.jupiter.api.Assertions.*;

public class MenuBarBuilderTest {

    private JFrame frame;
    private File packageJSONFile;
    private JDeployProjectEditorContext context;
    private MenuBarBuilder.MenuBarCallbacks callbacks;
    private MenuBarBuilder builder;
    private boolean[] callbacksInvoked;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI test in headless environment");
        frame = new JFrame("Test");
        packageJSONFile = tempDir.resolve("package.json").toFile();
        FileUtils.writeStringToFile(packageJSONFile, "{}", "UTF-8");

        context = new JDeployProjectEditorContext();

        // Track which callbacks are invoked
        callbacksInvoked = new boolean[7];

        callbacks = new MenuBarBuilder.MenuBarCallbacks() {
            @Override
            public void onSave() {
                callbacksInvoked[0] = true;
            }

            @Override
            public void onOpenInTextEditor() {
                callbacksInvoked[1] = true;
            }

            @Override
            public void onGenerateGithubWorkflow() {
                callbacksInvoked[2] = true;
            }

            @Override
            public void onEditGithubWorkflow() {
                callbacksInvoked[3] = true;
            }

            @Override
            public void onVerifyHomepage() {
                callbacksInvoked[4] = true;
            }

            @Override
            public void onSetupClaude() {
                callbacksInvoked[5] = true;
            }

            @Override
            public void onClose() {
                callbacksInvoked[6] = true;
            }
        };

        builder = new MenuBarBuilder(frame, packageJSONFile, context, callbacks);
    }

    @Test
    void testBuildCreatesMenuBar() {
        JMenuBar menuBar = builder.build();
        assertNotNull(menuBar, "MenuBar should not be null");
    }

    @Test
    void testMenuBarHasFileMenu() {
        JMenuBar menuBar = builder.build();
        assertNotNull(findMenuByName(menuBar, "File"), "File menu should exist");
    }

    @Test
    void testMenuBarHasHelpMenu() {
        JMenuBar menuBar = builder.build();
        assertNotNull(findMenuByName(menuBar, "Help"), "Help menu should exist");
    }

    @Test
    void testFileMenuHasSaveItem() {
        JMenuBar menuBar = builder.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuItemByText(fileMenu, "Save"), "Save item should exist in File menu");
    }

    @Test
    void testFileMenuHasOpenWithTextEditorItem() {
        JMenuBar menuBar = builder.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuItemByText(fileMenu, "Open with Text Editor"), 
                "Open with Text Editor item should exist in File menu");
    }

    @Test
    void testFileMenuHasOpenProjectDirectoryItem() {
        JMenuBar menuBar = builder.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuItemByText(fileMenu, "Open Project Directory"), 
                "Open Project Directory item should exist in File menu");
    }

    @Test
    void testFileMenuHasOpenInIdeSubmenu() {
        JMenuBar menuBar = builder.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuByName(fileMenu, "Open in IDE"), 
                "Open in IDE submenu should exist in File menu");
    }

    @Test
    void testFileMenuHasGithubWorkflowItems() {
        JMenuBar menuBar = builder.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuItemByText(fileMenu, "Create Github Workflow"), 
                "Create Github Workflow item should exist in File menu");
        assertNotNull(findMenuItemByText(fileMenu, "Edit Github Workflow"), 
                "Edit Github Workflow item should exist in File menu");
    }

    @Test
    void testFileMenuHasVerifyHomepageItem() {
        JMenuBar menuBar = builder.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuItemByText(fileMenu, "Verify Homepage"), 
                "Verify Homepage item should exist in File menu");
    }

    @Test
    void testFileMenuHasSetupClaudeItem() {
        JMenuBar menuBar = builder.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuItemByText(fileMenu, "Install Claude Code jDeploy Skill"),
                "Install Claude Code jDeploy Skill item should exist in File menu");
    }

    @Test
    void testHelpMenuHasJDeployHelpItem() {
        JMenuBar menuBar = builder.build();
        JMenu helpMenu = findMenuByName(menuBar, "Help");
        assertNotNull(helpMenu, "Help menu should exist");
        assertNotNull(findMenuItemByText(helpMenu, "jDeploy Help"), 
                "jDeploy Help item should exist in Help menu");
    }

    @Test
    void testHelpMenuHasWebsiteItem() {
        JMenuBar menuBar = builder.build();
        JMenu helpMenu = findMenuByName(menuBar, "Help");
        assertNotNull(helpMenu, "Help menu should exist");
        assertNotNull(findMenuItemByText(helpMenu, "jDeploy Website"), 
                "jDeploy Website item should exist in Help menu");
    }

    @Test
    void testHelpMenuHasDeveloperGuideItem() {
        JMenuBar menuBar = builder.build();
        JMenu helpMenu = findMenuByName(menuBar, "Help");
        assertNotNull(helpMenu, "Help menu should exist");
        assertNotNull(findMenuItemByText(helpMenu, "jDeploy Developers Guide"), 
                "jDeploy Developers Guide item should exist in Help menu");
    }

    @Test
    void testHelpMenuHasMailingListItem() {
        JMenuBar menuBar = builder.build();
        JMenu helpMenu = findMenuByName(menuBar, "Help");
        assertNotNull(helpMenu, "Help menu should exist");
        assertNotNull(findMenuItemByText(helpMenu, "jDeploy Developers Mailing List"), 
                "jDeploy Developers Mailing List item should exist in Help menu");
    }

    @Test
    void testHelpMenuHasSupportForumItem() {
        JMenuBar menuBar = builder.build();
        JMenu helpMenu = findMenuByName(menuBar, "Help");
        assertNotNull(helpMenu, "Help menu should exist");
        assertNotNull(findMenuItemByText(helpMenu, "Support Forum"), 
                "Support Forum item should exist in Help menu");
    }

    @Test
    void testHelpMenuHasIssueTrackerItem() {
        JMenuBar menuBar = builder.build();
        JMenu helpMenu = findMenuByName(menuBar, "Help");
        assertNotNull(helpMenu, "Help menu should exist");
        assertNotNull(findMenuItemByText(helpMenu, "Issue Tracker"), 
                "Issue Tracker item should exist in Help menu");
    }

    @Test
    void testCreateLinkItemReturnsMenuItem() {
        JMenuItem item = MenuBarBuilder.createLinkItem(
                "https://example.com",
                "Example Link",
                "This is a test link",
                context,
                frame
        );
        assertNotNull(item, "createLinkItem should return a non-null JMenuItem");
        assertEquals("Example Link", item.getText(), "Menu item text should match");
        assertEquals("This is a test link", item.getToolTipText(), "Menu item tooltip should match");
    }

    @Test
    void testCreateHelpButtonReturnsButton() {
        JButton btn = MenuBarBuilder.createHelpButton(
                "https://example.com",
                "Example Help",
                "This is test help",
                context,
                frame
        );
        assertNotNull(btn, "createHelpButton should return a non-null JButton");
        assertEquals("Example Help", btn.getText(), "Button text should match");
        assertEquals("This is test help", btn.getToolTipText(), "Button tooltip should match");
    }

    @Test
    void testExitMenuItemDisplayedWhenContextAllows() {
        // Create a mock context that allows exit menu
        JDeployProjectEditorContext allowExitContext = new JDeployProjectEditorContext() {
            @Override
            public boolean shouldDisplayExitMenu() {
                return true;
            }
        };
        
        MenuBarBuilder builderWithExit = new MenuBarBuilder(frame, packageJSONFile, allowExitContext, callbacks);
        JMenuBar menuBar = builderWithExit.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNotNull(findMenuItemByText(fileMenu, "Exit"), 
                "Exit item should exist in File menu when context allows");
    }

    @Test
    void testExitMenuItemHiddenWhenContextDisallows() {
        // Create a context that disallows exit menu (default behavior)
        JDeployProjectEditorContext noExitContext = new JDeployProjectEditorContext() {
            @Override
            public boolean shouldDisplayExitMenu() {
                return false;
            }
        };
        
        MenuBarBuilder builderNoExit = new MenuBarBuilder(frame, packageJSONFile, noExitContext, callbacks);
        JMenuBar menuBar = builderNoExit.build();
        JMenu fileMenu = findMenuByName(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");
        assertNull(findMenuItemByText(fileMenu, "Exit"), 
                "Exit item should not exist in File menu when context disallows");
    }

    @Test
    void testGetGenerateGithubWorkflowMenuItem() {
        builder.build();
        JMenuItem item = builder.getGenerateGithubWorkflowMenuItem();
        assertNotNull(item, "getGenerateGithubWorkflowMenuItem should return non-null");
        assertEquals("Create Github Workflow", item.getText(), "Generate Github Workflow menu item text should match");
    }

    @Test
    void testGetEditGithubWorkflowMenuItem() {
        builder.build();
        JMenuItem item = builder.getEditGithubWorkflowMenuItem();
        assertNotNull(item, "getEditGithubWorkflowMenuItem should return non-null");
        assertEquals("Edit Github Workflow", item.getText(), "Edit Github Workflow menu item text should match");
    }

    // Helper methods
    private JMenu findMenuByName(JMenuBar menuBar, String name) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && name.equals(menu.getText())) {
                return menu;
            }
        }
        return null;
    }

    private JMenu findMenuByName(JMenu parent, String name) {
        for (int i = 0; i < parent.getItemCount(); i++) {
            JMenuItem item = parent.getItem(i);
            if (item instanceof JMenu) {
                JMenu menu = (JMenu) item;
                if (name.equals(menu.getText())) {
                    return menu;
                }
            }
        }
        return null;
    }

    private JMenuItem findMenuItemByText(JMenu menu, String text) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && text.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }
}
