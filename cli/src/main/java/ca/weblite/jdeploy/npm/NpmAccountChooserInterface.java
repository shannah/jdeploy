package ca.weblite.jdeploy.npm;

import java.util.List;
import java.util.concurrent.Future;

public interface NpmAccountChooserInterface {
    Future<NpmAccountInterface> selectNpmAccount(List<NpmAccountInterface> accounts);
    Future<NpmAccountInterface> createNpmAccount();
}
