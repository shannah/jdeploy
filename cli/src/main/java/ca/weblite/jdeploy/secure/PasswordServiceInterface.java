package ca.weblite.jdeploy.secure;

import java.util.concurrent.CompletableFuture;

public interface PasswordServiceInterface {
    CompletableFuture<char[]> getPassword(String name, String prompt);
    CompletableFuture<Void> setPassword(String name, char[] password);

    CompletableFuture<Void> removePassword(String name);
}
