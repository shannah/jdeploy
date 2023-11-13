package ca.weblite.jdeploy.services;

import javax.inject.Singleton;

@Singleton
public class GithubTokenService {
    public String getToken() {
        return System.getenv("GH_TOKEN");
    }
}
