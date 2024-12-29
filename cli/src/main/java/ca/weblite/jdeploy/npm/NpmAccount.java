package ca.weblite.jdeploy.npm;

public class NpmAccount implements NpmAccountInterface {

    private final String npmAccountName;
    private final String npmToken;

    public NpmAccount(String npmAccountName, String npmToken){
        this.npmAccountName = npmAccountName;
        this.npmToken = npmToken;
    }
    @Override
    public String getNpmAccountName() {
        return npmAccountName;
    }

    @Override
    public String getNpmToken() {
        return npmToken;
    }
}
