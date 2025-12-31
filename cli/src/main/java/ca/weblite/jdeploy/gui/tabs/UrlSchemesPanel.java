package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.util.SwingUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Panel for managing URL schemes (custom protocol handlers).
 * Allows users to specify comma-separated URL schemes that will launch their application.
 *
 * Follows the panel pattern: getRoot(), load(JSONObject), save(JSONObject), addChangeListener()
 */
public class UrlSchemesPanel extends JPanel {
    private JTextField urlSchemesField;
    private ActionListener changeListener;

    public UrlSchemesPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Help text
        JTextArea helpText = new JTextArea();
        helpText.setEditable(false);
        helpText.setOpaque(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setText(
                "Create one or more custom URL schemes that will trigger your app to launch when users try to open a " +
                        "link in their web browser with one of them.\nEnter one or more URL schemes separated by commas " +
                        "in the field below." +
                "\n\nFor example, if you want links like mynews:foobarfoo and mymusic:fuzzbazz to launch your app, then " +
                "add 'mynews, mymusic' to the field below."
        );
        helpText.setMaximumSize(new Dimension(600, 150));

        // URL schemes field
        urlSchemesField = new JTextField();
        urlSchemesField.setToolTipText("Comma-delimited list of URL schemes to associate with your application.");
        urlSchemesField.setMaximumSize(new Dimension(400, urlSchemesField.getPreferredSize().height));

        // Add change listener to the text field
        SwingUtils.addChangeListenerTo(urlSchemesField, this::fireChangeEvent);

        // Add components to panel
        add(helpText);
        add(Box.createVerticalStrut(10));
        add(urlSchemesField);
        add(Box.createVerticalGlue());
    }

    /**
     * Returns the root component (this panel itself).
     */
    public JPanel getRoot() {
        return this;
    }

    /**
     * Loads URL schemes configuration from the jdeploy JSONObject.
     * Converts JSONArray of schemes to comma-separated string in the text field.
     */
    public void load(JSONObject jdeploy) {
        if (jdeploy.has("urlSchemes")) {
            JSONArray schemes = jdeploy.getJSONArray("urlSchemes");
            int len = schemes.length();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(schemes.getString(i).trim());
            }
            urlSchemesField.setText(sb.toString());
        } else {
            urlSchemesField.setText("");
        }
    }

    /**
     * Saves URL schemes configuration to the jdeploy JSONObject.
     * Converts comma-separated string from text field to JSONArray.
     */
    public void save(JSONObject jdeploy) {
        String[] schemesArr = urlSchemesField.getText().split(",");
        int len = schemesArr.length;
        JSONArray arr = new JSONArray();
        int idx = 0;
        for (int i = 0; i < len; i++) {
            String scheme = schemesArr[i].trim();
            if (!scheme.isEmpty()) {
                arr.put(idx++, scheme);
            }
        }
        
        if (arr.length() > 0) {
            jdeploy.put("urlSchemes", arr);
        } else if (jdeploy.has("urlSchemes")) {
            jdeploy.remove("urlSchemes");
        }
    }

    /**
     * Registers a change listener to be notified when URL schemes change.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }
}
