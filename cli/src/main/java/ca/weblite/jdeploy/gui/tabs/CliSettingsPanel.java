package ca.weblite.jdeploy.gui.tabs;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.net.URI;

import org.json.JSONObject;

public class CliSettingsPanel {
    private JPanel root;
    private JTextField commandField;
    private JButton tutorialButton;
    private ActionListener changeListener;
    
    private static final String JDEPLOY_WEBSITE_URL = System.getProperty("jdeploy.website.url", "https://www.jdeploy.com/");

    public CliSettingsPanel() {
        initializeUI();
    }

    private void initializeUI() {
        root = new JPanel();
        root.setOpaque(false);
        root.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        // Help panel with help button
        JPanel helpPanel = new JPanel();
        helpPanel.setOpaque(false);
        helpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        helpPanel.setMaximumSize(new Dimension(10000, helpPanel.getPreferredSize().height));
        
        JButton helpButton = new JButton("Help");
        helpButton.setToolTipText("Learn more about this tab in the help guide.");
        helpPanel.add(helpButton);
        
        root.add(helpPanel);

        // Tutorial description
        JLabel tutorialLabel = new JLabel(
                "<html>" +
                "<p style='width:400px'>" +
                "Your app will also be installable and runnable as a command-line app using npm/npx.  " +
                "See the CLI tutorial for more details" +
                "</p>" +
                "</html>"
        );
        root.add(tutorialLabel);

        // Tutorial button
        tutorialButton = new JButton("Open CLI Tutorial");
        tutorialButton.setMaximumSize(tutorialButton.getPreferredSize());
        root.add(tutorialButton);

        root.add(Box.createVerticalStrut(10));

        // Command field description
        JLabel commandDescription = new JLabel(
                "<html>" +
                "<p style='width:400px'>" +
                "The following field allows you to specify the command name for your app.  " +
                "Users will launch your app by entering this name in the command-line. " +
                "</p>" +
                "</html>"
        );
        root.add(commandDescription);

        // Command field
        commandField = new JTextField();
        commandField.setMaximumSize(new Dimension(1000, commandField.getPreferredSize().height));
        root.add(commandField);

        // Vertical glue to push everything to top
        root.add(new Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(10, 1000)));

        // Add document listener to command field
        addChangeListenerTo(commandField, this::fireChangeEvent);
    }

    public JPanel getRoot() {
        return root;
    }

    public JTextField getCommandField() {
        return commandField;
    }

    public JButton getTutorialButton() {
        return tutorialButton;
    }

    public void load(JSONObject jdeploy) {
        if (jdeploy == null || !jdeploy.has("bin")) {
            commandField.setText("");
            return;
        }

        JSONObject bin = jdeploy.getJSONObject("bin");
        if (bin.keySet().size() == 1) {
            commandField.setText(bin.keySet().iterator().next());
        } else {
            commandField.setText("");
        }
    }

    public void save(JSONObject jdeploy) {
        String command = commandField.getText().trim();
        
        if (command.isEmpty()) {
            // Remove bin if empty
            if (jdeploy.has("bin")) {
                jdeploy.remove("bin");
            }
        } else {
            JSONObject bin = new JSONObject();
            bin.put(command, "jdeploy-bundle/jdeploy.js");
            jdeploy.put("bin", bin);
        }
    }

    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }

    private static void addChangeListenerTo(JTextComponent textField, Runnable callback) {
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                callback.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                callback.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                callback.run();
            }
        });
    }
}
