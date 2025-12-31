package ca.weblite.jdeploy.gui.util;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * Utility methods for Swing UI components.
 */
public final class SwingUtils {
    
    private SwingUtils() {
        // Prevent instantiation
    }
    
    /**
     * Adds a document listener to the given text component that invokes the callback
     * on any text change (insert, remove, or update).
     *
     * @param textField the text component to listen to
     * @param callback the callback to invoke on changes
     */
    public static void addChangeListenerTo(JTextComponent textField, Runnable callback) {
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
