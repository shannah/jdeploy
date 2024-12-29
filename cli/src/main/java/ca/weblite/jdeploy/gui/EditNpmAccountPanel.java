package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.npm.NpmAccount;
import ca.weblite.jdeploy.npm.NpmAccountInterface;

import javax.swing.*;

public class EditNpmAccountPanel extends JPanel {
    private JTextField accountNameField;
    private JPasswordField npmTokenField;

    public EditNpmAccountPanel(NpmAccountInterface account) {
        super();

        // Create the layout
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);

        // Create the components
        JLabel accountNameLabel = new JLabel("Account Name");
        accountNameField = new JTextField(30);


        JLabel npmTokenLabel = new JLabel("NPM Token");
        npmTokenField = new JPasswordField(30);

        // Set the account name
        accountNameField.setText(account.getNpmAccountName());

        // Set the NPM token
        if (account.getNpmToken() != null) {
            npmTokenField.setText(account.getNpmToken());
        }

        // Set the layout
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(accountNameLabel)
                    .addComponent(npmTokenLabel)
                )
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(accountNameField)
                    .addComponent(npmTokenField)
                )
        );

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(accountNameLabel)
                    .addComponent(accountNameField)
                )
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(npmTokenLabel)
                    .addComponent(npmTokenField)
                )
        );


    }


    public JPasswordField getNpmTokenField() {
        return npmTokenField;
    }

    public JTextField getAccountNameField() {
        return accountNameField;
    }
}
