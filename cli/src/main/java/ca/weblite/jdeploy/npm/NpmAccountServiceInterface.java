package ca.weblite.jdeploy.npm;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface NpmAccountServiceInterface {
    CompletableFuture<List<NpmAccountInterface>> getNpmAccounts();
    CompletableFuture<Void> saveNpmAccount(NpmAccountInterface account);

    CompletableFuture<Void> removeNpmAccount(NpmAccountInterface account);

    CompletableFuture<NpmAccountInterface> loadNpmAccount(NpmAccountInterface accountName);
}
