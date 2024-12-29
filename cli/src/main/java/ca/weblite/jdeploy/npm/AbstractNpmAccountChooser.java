package ca.weblite.jdeploy.npm;

import java.util.List;
import java.util.concurrent.Future;

public abstract class AbstractNpmAccountChooser implements NpmAccountChooserInterface{

    private final NpmAccountServiceInterface npmAccountService;

    public AbstractNpmAccountChooser(NpmAccountServiceInterface npmAccountService) {
        this.npmAccountService = npmAccountService;
    }

    public abstract Future<NpmAccountInterface> selectNpmAccount(List<NpmAccountInterface> accounts);

    public abstract Future<NpmAccountInterface> createNpmAccount();
}
