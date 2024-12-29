package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.npm.NpmAccountInterface;
import org.kordamp.ikonli.swing.FontIcon;
import org.kordamp.ikonli.material.Material;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class NpmAccountChooserPanel extends JPanel {

    private NpmAccountInterface selectedAccount;

    // For storing ActionListeners that external code may register
    private final EventListenerList listenerList = new EventListenerList();

    private final JButton newAccountButton = new JButton("New Account");

    public NpmAccountChooserPanel(List<NpmAccountInterface> accounts) {
        // Vertical box layout
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(320, 240));

        // For each account, create a flat, full-width button with an icon
        for (NpmAccountInterface account : accounts) {
            JButton button = new JButton(account.getNpmAccountName());

            // ---- Make the button "flat" (no border, no background fill) ----
            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.setContentAreaFilled(false);

            // ---- Stretch to full width in a BoxLayout (Y_AXIS) ----
            // Setting maximumSize ensures it can expand horizontally
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(
                    new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height)
            );

            // ---- Add a Material icon ----
            // Replace "/icons/account.png" with your actual icon path or library call
            // e.g. Icon materialIcon = ... (from Ikonli or other library)
            Icon materialIcon = loadMaterialIcon();
            if (materialIcon != null) {
                button.setIcon(materialIcon);
            }

            // Button action: select the account, fire event
            button.addActionListener(e -> {
                selectedAccount = account;
                fireAccountSelected(account);
            });

            add(button);
        }

        // Similarly, style the "New Account" button if desired
        newAccountButton.setBorderPainted(false);
        newAccountButton.setFocusPainted(false);
        newAccountButton.setContentAreaFilled(false);
        newAccountButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        newAccountButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, newAccountButton.getPreferredSize().height)
        );

        add(newAccountButton);
    }

    /**
     * Example method to load a Material-style icon from your resources.
     * Adjust this to your actual icon resource or library usage.
     */
    private Icon loadMaterialIcon() {
        // Suppose you have an icon resource in your resources folder at "/icons/account.png"
        // If resource doesn't exist, this will return null.
        return FontIcon.of(Material.ACCOUNT_CIRCLE, 24);

    }

    /**
     * Registers an ActionListener so external code can be notified
     * when a user selects an account.
     */
    public void addActionListener(ActionListener listener) {
        listenerList.add(ActionListener.class, listener);
    }

    /**
     * Unregisters an ActionListener.
     */
    public void removeActionListener(ActionListener listener) {
        listenerList.remove(ActionListener.class, listener);
    }

    public JButton getNewAccountButton() {
        return newAccountButton;
    }

    /**
     * Helper method to fire an ActionEvent to all registered ActionListeners.
     * The ActionCommand is set to the selected account's name.
     */
    private void fireAccountSelected(NpmAccountInterface account) {
        ActionEvent event = new ActionEvent(
                this,
                ActionEvent.ACTION_PERFORMED,
                account.getNpmAccountName()
        );
        for (ActionListener listener : listenerList.getListeners(ActionListener.class)) {
            listener.actionPerformed(event);
        }
    }

    public NpmAccountInterface getSelectedAccount() {
        return selectedAccount;
    }
}
