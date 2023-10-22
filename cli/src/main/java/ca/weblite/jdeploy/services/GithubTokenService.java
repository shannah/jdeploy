package ca.weblite.jdeploy.services;

public class GithubTokenService {
    public String getToken() {
        return System.getenv("GH_TOKEN");
    }
}
