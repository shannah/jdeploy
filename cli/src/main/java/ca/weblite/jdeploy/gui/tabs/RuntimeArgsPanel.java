package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionListener;

public class RuntimeArgsPanel extends JPanel {
    private JTextArea argsField;
    private ActionListener changeListener;

    public RuntimeArgsPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Create the text area for arguments
        argsField = new JTextArea();
        argsField.setLineWrap(false);
        argsField.setWrapStyleWord(false);
        argsField.setRows(8);
        argsField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // Create top panel with label and description
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        topPanel.setOpaque(false);

        JLabel runArgsLabel = new JLabel("Runtime Arguments");
        JLabel runArgsDescription = new JLabel("<html>" +
                "<p style='font-size:x-small;width:400px'>One argument per line.<br/></p>" +
                "<p style='font-size:x-small; width:400px'>Prefix system properties with '-D'.  E.g. -Dfoo=bar</p>" +
                "<p style='font-size:x-small;width:400px'>Prefix JVM options with '-X'.  E.g. -Xmx2G</p><br/>" +
                "<p style='font-size:x-small;width:400px;padding-top:1em'><strong>Placeholder Variables</strong><br/>" +
                "<strong>{{ user.home }}</strong> : The user's home directory<br/>" +
                "<strong>{{ exe.path }}</strong> : The path to the program executable.<br/>" +
                "<strong>{{ app.path }}</strong> : " +
                "The path to the .app bundle on Mac.  Falls back to executable path on other platforms.<br/>" +
                "</p><br/>" +
                "<p style='font-size:x-small;width:400px;padding-top:1em'>" +
                "<strong>Platform-Specific Arguments:</strong><br/>" +
                "Platform-specific arguments are only added on specific platforms.<br/>" +
                "<strong>Property Args:</strong> " +
                "-D[PLATFORMS]foo=bar, where PLATFORMS is mac, win, or linux, or pipe-concatenated list. " +
                " E.g. '-D[mac]foo=bar', '-D[win]foo=bar', '-D[linux]foo=bar', '-D[mac|linux]foo=bar', etc...<br/>" +
                "<strong>JVM Options:</strong> " +
                "-X[PLATFORMS]foo, where PLATFORMS is mac, win, or linux, or pipe-concatenated list.  " +
                "E.g. '-X[mac]foo', '-X[win]foo', '-X[linux]foo', '-X[mac|linux]foo', etc...<br/>" +
                "<strong>Program Args:</strong> " +
                "-[PLATFORMS]foo, where PLATFORMS is mac, win, or linux, or pipe-concatenated list.  " +
                "E.g. '-[mac]foo', '-[win]foo', '-[linux]foo', '-[mac|linux]foo', etc...<br/>" +
                "</p>" +
                "</html>");
        runArgsDescription.setBorder(new EmptyBorder(10, 10, 10, 10));

        topPanel.add(runArgsLabel, BorderLayout.CENTER);
        topPanel.add(runArgsDescription, BorderLayout.SOUTH);

        // Create scroller for the text area
        JScrollPane argsScroller = new JScrollPane(argsField);
        argsScroller.setOpaque(false);
        argsScroller.getViewport().setOpaque(false);

        // Add components to main panel
        add(topPanel, BorderLayout.NORTH);
        add(argsScroller, BorderLayout.CENTER);
    }

    public JPanel getRoot() {
        return this;
    }

    public JTextArea getArgsField() {
        return argsField;
    }

    /**
     * Load runtime arguments from a jdeploy JSONObject.
     * Arguments are stored as a JSONArray in the "args" field.
     */
    public void load(JSONObject jdeploy) {
        if (jdeploy == null) {
            argsField.setText("");
            return;
        }

        if (jdeploy.has("args")) {
            JSONArray jarr = jdeploy.getJSONArray("args");
            int len = jarr.length();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                if (sb.length() > 0) {
                    sb.append(System.lineSeparator());
                }
                sb.append(jarr.getString(i).trim());
            }
            argsField.setText(sb.toString());
        } else {
            argsField.setText("");
        }
    }

    /**
     * Save runtime arguments to a jdeploy JSONObject.
     * Parses newline-separated arguments and stores as a JSONArray.
     * Removes the "args" field if empty.
     */
    public void save(JSONObject jdeploy) {
        if (jdeploy == null) {
            return;
        }

        String[] parts = argsField.getText().split("\n");
        JSONArray jarr = new JSONArray();
        int index = 0;
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            jarr.put(index++, part);
        }

        if (jarr.length() == 0) {
            jdeploy.remove("args");
        } else {
            jdeploy.put("args", jarr);
        }
    }

    /**
     * Add a change listener that fires when arguments are modified.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
        addChangeListenerTo(argsField, this::fireChangeEvent);
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(null);
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
