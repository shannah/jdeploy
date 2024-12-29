package ca.weblite.jdeploy.secure;

import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of PasswordServiceInterface that uses the cross-platform
 * java-keyring library to store/retrieve/remove passwords on Windows
 * (via Windows Credential Manager), macOS Keychain, or Linux keyrings.
 */
@Singleton
public class JavaKeyringPasswordService implements PasswordServiceInterface {

    // This is the "service name" or "application ID" used for storing credentials.
    // You can choose any string; it simply labels your credentials in the keyring.
    private static final String SERVICE_NAME = "com.jdeploy";

    @Override
    public CompletableFuture<char[]> getPassword(String name, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // The prompt parameter is ignored in this example,
                // but you could use it to display a UI message if desired.

                Keyring keyring = Keyring.create();
                // Retrieve the password associated with (service=SERVICE_NAME, account=name).
                System.out.println("Getting password for " + name + " from keyring");
                String password = keyring.getPassword(SERVICE_NAME, name);
                System.out.println("Gotten password from keyring: " + password);

                // Return as char[] if present, or null if not found.
                return password != null ? password.toCharArray() : null;
            } catch (PasswordAccessException e) {
                throw new RuntimeException(e);
            } catch (BackendNotSupportedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setPassword(String name, char[] password) {
        return CompletableFuture.runAsync(() -> {
            try {
                Keyring keyring = Keyring.create();
                // Convert the char[] to a String for storing in the keyring
                String passStr = password != null ? new String(password) : null;

                // If password is null or empty, you might remove the credential instead.
                if (passStr == null || passStr.isEmpty()) {
                    keyring.deletePassword(SERVICE_NAME, name);
                } else {
                    // Store the password for (service=SERVICE_NAME, account=name).
                    keyring.setPassword(SERVICE_NAME, name, passStr);
                }

                // Optionally, overwrite the passStr memory if you want to reduce its exposure
                // but keep in mind Java strings are immutable. You might do more rigorous scrubbing.
            } catch (BackendNotSupportedException e) {
                throw new RuntimeException("Error setting password in keyring", e);
            } catch (PasswordAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> removePassword(String name) {
        return CompletableFuture.runAsync(() -> {
            try {
                Keyring keyring = Keyring.create();
                // Removes the credential from the keyring if it exists
                keyring.deletePassword(SERVICE_NAME, name);
            } catch (BackendNotSupportedException e) {
                throw new RuntimeException("Error removing password from keyring", e);
            } catch (PasswordAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
