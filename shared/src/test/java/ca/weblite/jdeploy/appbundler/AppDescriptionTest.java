package ca.weblite.jdeploy.appbundler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AppDescription splash data URI functionality.
 */
public class AppDescriptionTest {

    @Test
    public void testSplashDataURIGetterSetter() {
        AppDescription app = new AppDescription();
        String testDataURI = "data:text/html;base64,PGh0bWw+PGJvZHk+VGVzdDwvYm9keT48L2h0bWw+";

        app.setSplashDataURI(testDataURI);
        assertEquals(testDataURI, app.getSplashDataURI());
    }

    @Test
    public void testSplashDataURIDefaultsToNull() {
        AppDescription app = new AppDescription();
        assertNull(app.getSplashDataURI());
    }

    @Test
    public void testSplashDataURICanBeSetToNull() {
        AppDescription app = new AppDescription();
        app.setSplashDataURI("data:text/html;base64,test");
        app.setSplashDataURI(null);
        assertNull(app.getSplashDataURI());
    }

    @Test
    public void testSplashDataURIIndependentOfIconDataURI() {
        AppDescription app = new AppDescription();
        String iconURI = "data:image/png;base64,icondata";
        String splashURI = "data:text/html;base64,splashdata";

        app.setIconDataURI(iconURI);
        app.setSplashDataURI(splashURI);

        assertEquals(iconURI, app.getIconDataURI());
        assertEquals(splashURI, app.getSplashDataURI());
    }
}
