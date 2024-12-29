package ca.weblite.jdeploy.npm;

import ca.weblite.jdeploy.secure.PasswordServiceInterface;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

@Singleton
public class PreferencesNpmAccountService implements NpmAccountServiceInterface {

    private final PasswordServiceInterface passwordService;

    @Inject
    public PreferencesNpmAccountService(PasswordServiceInterface passwordService) {
        this.passwordService = passwordService;
    }

    /**
     * We'll store account names in a sub-node of Preferences. For example:
     * preferences root: /ca/weblite/jdeploy/npm/PreferencesNpmAccountService
     *
     * Under this node, we can create a child node "npmAccounts" in which each account name
     * is stored as a key. The key is the account name, and the value is also the account name,
     * so that we can iterate over them in #getNpmAccounts().
     */
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(PreferencesNpmAccountService.class)
                    .node("npmAccounts");

    @Override
    public CompletableFuture<List<NpmAccountInterface>> getNpmAccounts() {
        return CompletableFuture.supplyAsync(() -> {
            List<NpmAccountInterface> accounts = new ArrayList<>();
            try {
                // Retrieve all keys (each key is an npm account name)
                String[] keys = PREFS.keys();
                for (String accountNameKey : keys) {
                    // The stored value for each key might just be the account name itself
                    String storedAccountName = PREFS.get(accountNameKey, null);
                    if (storedAccountName != null) {
                        // The token is not loaded here (null),
                        // as per requirement: "getNpmAccounts() should return objects with null for the token."
                        NpmAccountInterface account = new NpmAccount(storedAccountName, null);
                        accounts.add(account);
                    }
                }
            } catch (BackingStoreException e) {
                e.printStackTrace();
            }
            return Collections.unmodifiableList(accounts);
        });
    }

    @Override
    public CompletableFuture<Void> saveNpmAccount(NpmAccountInterface account) {
        return CompletableFuture.runAsync(() -> {
            // 1. Save only the npmAccountName in preferences
            String accountName = account.getNpmAccountName();
            PREFS.put(accountName, accountName);

            // 2. Save the token in the system keychain
            String token = account.getNpmToken();
            if (token != null) {
                passwordService.setPassword(accountName, token.toCharArray()).join();
            }
        });
    }

    @Override
    public CompletableFuture<Void> removeNpmAccount(NpmAccountInterface account) {
        return CompletableFuture.runAsync(() -> {
            String accountName = account.getNpmAccountName();

            // 1. Remove the account from preferences
            PREFS.remove(accountName);

            // 2. Remove the keychain entry for the token
            passwordService.removePassword(accountName).join();
        });
    }

    @Override
    public CompletableFuture<NpmAccountInterface> loadNpmAccount(NpmAccountInterface account) {
        return CompletableFuture.supplyAsync(() -> {
            // "loadNpmAccount() should load the token of the given account from the keychain,
            // and return a copy of the provided NpmAccount, but with the token."

            String accountName = account.getNpmAccountName();
            // Load the token from the system keychain
            CompletableFuture<char[]> token = passwordService.getPassword(accountName, "Load NPM token from keychain");

            // Return a copy of the provided NpmAccount, but with the token
            return token.thenApply(t -> new NpmAccount(accountName, new String(t)))
                    .join();
        });
    }
}
