package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.npm.NpmAccountInterface;

import javax.swing.*;
import java.awt.*;

public class EditNpmAccountDialog extends JDialog {
    private EditNpmAccountPanel editNpmAccountPanel;
    private JButton saveButton;
    private JButton cancelButton;
    public EditNpmAccountDialog(Window parent, NpmAccountInterface account) {
        super(parent);
        // Create the layout
        GroupLayout layout = new GroupLayout(this.getContentPane());
        this.getContentPane().setLayout(layout);

        // Create the components
        editNpmAccountPanel = new EditNpmAccountPanel(account);
        saveButton = new JButton("Save");
        cancelButton = new JButton("Cancel");

        // Set the layout
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(editNpmAccountPanel)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(saveButton)
                    .addComponent(cancelButton)
                )
        );

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addComponent(editNpmAccountPanel)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(saveButton)
                    .addComponent(cancelButton)
                )
        );
        pack();
    }

    public JTextField getAccountNameField() {
        return editNpmAccountPanel.getAccountNameField();
    }

    public JPasswordField getNpmTokenField() {
        return editNpmAccountPanel.getNpmTokenField();
    }

    public JButton getSaveButton() {
        return saveButton;
    }

    public JButton getCancelButton() {
        return cancelButton;
    }

}
