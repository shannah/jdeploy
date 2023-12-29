package ca.weblite.intellij.jdeployintellijplugin;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;

import javax.inject.Singleton;
import java.util.prefs.Preferences;
@Singleton
public class GithubPreferences {
    private static final String PREFERENCES_PATH = "ca.weblite.intellij.jdeployintellijplugin";
    private static final String TOKEN_KEY = "githubToken";

    private static final String USER_KEY = "githubUser";

    // Set a user preference
    public void setUserPreference(String key, String value) {
        Preferences prefs = Preferences.userRoot().node(PREFERENCES_PATH);
        prefs.put(key, value);
    }

    // Get a user preference
    public String getUserPreference(String key, String defaultValue) {
        Preferences prefs = Preferences.userRoot().node(PREFERENCES_PATH);
        return prefs.get(key, defaultValue);
    }

    public String getGithubUser() {
        return getUserPreference(USER_KEY, "");
    }

    public String setGithubUser(String user) {
        setUserPreference(USER_KEY, user);
        return user;
    }

    // Set the token in the PasswordSafe
    public void setToken(String token) {
        PasswordSafe.getInstance().setPassword(getCredentialAttributes(), token);
    }

    // Get the token from the PasswordSafe
    public String getToken() {
        return PasswordSafe.getInstance().getPassword(getCredentialAttributes());
    }

    private CredentialAttributes getCredentialAttributes() {
        return new CredentialAttributes("JDeploy", TOKEN_KEY);
    }
}
