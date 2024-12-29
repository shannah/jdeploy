package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.gui.NpmAccountChooserDialog;
import ca.weblite.jdeploy.gui.SwingExecutor;
import ca.weblite.jdeploy.npm.NpmAccount;
import ca.weblite.jdeploy.npm.NpmAccountInterface;
import ca.weblite.jdeploy.npm.NpmAccountServiceInterface;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class NpmAccountChooserController {

    private static final Executor EDT_EXECUTOR = new SwingExecutor();

    private final NpmAccountServiceInterface npmAccountService;

    private final Frame parentFrame;

    private NpmAccountInterface selectedAccount;

    public NpmAccountChooserController(Frame parentFrame, NpmAccountServiceInterface npmAccountService) {
        this.npmAccountService = npmAccountService;
        this.parentFrame = parentFrame;
    }

    public CompletableFuture<NpmAccountInterface> show() {
        return npmAccountService.getNpmAccounts().thenComposeAsync(this::showDialog, EDT_EXECUTOR);
    }

    public CompletableFuture<NpmAccountInterface> showDialog(List<NpmAccountInterface> accounts) {
        NpmAccountChooserDialog dialog = new NpmAccountChooserDialog(parentFrame, accounts);
        JButton newAccountButton = dialog.getNewAccountButton();
        newAccountButton.addActionListener(evt -> {
           EditNpmAccountController controller = new EditNpmAccountController(
                   dialog,
                   new NpmAccount("", null),
                   npmAccountService
           ) {
               @Override
               protected void afterSave(NpmAccountInterface account) {
                   selectedAccount = account;
                   dialog.dispose();
                   parentFrame.requestFocus();
               }
           };
           controller.show();

        });
        NpmAccountInterface sel = dialog.showDialog();
        if (sel != null) {
            selectedAccount = sel;
        }
        if (selectedAccount != null) {
            System.out.println("Selected account is " + selectedAccount.getNpmAccountName());
            return npmAccountService.loadNpmAccount(selectedAccount).thenApplyAsync(account -> {

                if (account == null) {
                    System.out.println("Account not loaded: " + selectedAccount.getNpmAccountName());
                    return selectedAccount;
                }
                System.out.println("Account loaded: " + account.getNpmAccountName());
                System.out.println("Token: " + account.getNpmToken());
                return account;
            }, EDT_EXECUTOR);
        }
        return CompletableFuture.completedFuture(selectedAccount);
    }
}
