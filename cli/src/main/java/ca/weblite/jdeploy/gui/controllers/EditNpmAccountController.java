package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.gui.EditNpmAccountDialog;
import ca.weblite.jdeploy.npm.NpmAccount;
import ca.weblite.jdeploy.npm.NpmAccountInterface;
import ca.weblite.jdeploy.npm.NpmAccountServiceInterface;
import ca.weblite.jdeploy.gui.SwingExecutor;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Executor;

public abstract class EditNpmAccountController {

    private static final Executor EDT_EXECUTOR = new SwingExecutor();

    private final EditNpmAccountDialog dialog;

    private final Window parentFrame;

    private final NpmAccountServiceInterface npmAccountService;

    private NpmAccountInterface newAccount;

    public EditNpmAccountController(
            Window parentFrame,
            NpmAccountInterface account,
            NpmAccountServiceInterface npmAccountService
    ) {
        this.parentFrame = parentFrame;
        this.dialog = new EditNpmAccountDialog(parentFrame, account);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        this.npmAccountService = npmAccountService;
        setupSaveButton();
        setupCancelButton();

    }

    public void show() {

        dialog.setVisible(true);
    }

    protected abstract void afterSave(NpmAccountInterface account);

    private NpmAccountInterface getAccount() {
        return new NpmAccount(
                dialog.getAccountNameField().getText(),
                isEmpty(dialog.getNpmTokenField())
                        ? null :
                        new String(dialog.getNpmTokenField().getPassword())
        );
    }

    private boolean isEmpty(JPasswordField field) {
        return field.getPassword().length == 0;
    }

    private boolean isAccountValid() {
        return !dialog.getAccountNameField().getText().isEmpty() && !isEmpty(dialog.getNpmTokenField());
    }

    private void update() {
        dialog.getSaveButton().setEnabled(isAccountValid());
    }

    private void setupSaveButton() {
        dialog.getSaveButton().addActionListener(e -> {
            newAccount = getAccount();
            npmAccountService.saveNpmAccount(newAccount).thenAcceptAsync(result->{
                afterSave(newAccount);
                dialog.dispose();
                parentFrame.requestFocus();
            }, EDT_EXECUTOR).exceptionally(t -> {
                EventQueue.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            dialog,
                            "Failed to save account: " + t.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
                return null;
            });
        });
    }

    private void setupCancelButton() {
        dialog.getCancelButton().addActionListener(e -> {
            newAccount = null;
            dialog.dispose();
            parentFrame.requestFocus();
        });
    }
}
