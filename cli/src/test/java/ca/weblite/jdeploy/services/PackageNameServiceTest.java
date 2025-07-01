package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class PackageNameServiceTest {

    private PackageNameService service;

    @BeforeEach
    void setUp() {
        service = new PackageNameService();
    }

    @Test
    void returnsPackageNameWhenTargetIsNpm() {
        PublishTargetInterface target = Mockito.mock(PublishTargetInterface.class);
        Mockito.when(target.getType()).thenReturn(PublishTargetType.NPM);
        Mockito.when(target.getUrl()).thenReturn(null); // Defensive

        String result = service.getFullPackageName(target, "my-package");
        assertEquals("my-package", result);
    }

    @Test
    void returnsPackageNameWhenUrlIsNull() {
        PublishTargetInterface target = Mockito.mock(PublishTargetInterface.class);
        Mockito.when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        Mockito.when(target.getUrl()).thenReturn(null);

        String result = service.getFullPackageName(target, "my-package");
        assertEquals("my-package", result);
    }

    @Test
    void returnsPackageNameWhenUrlIsEmpty() {
        PublishTargetInterface target = Mockito.mock(PublishTargetInterface.class);
        Mockito.when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        Mockito.when(target.getUrl()).thenReturn("");

        String result = service.getFullPackageName(target, "my-package");
        assertEquals("my-package", result);
    }

    @Test
    void returnsFullPackageNameWhenUrlIsPresent() {
        PublishTargetInterface target = Mockito.mock(PublishTargetInterface.class);
        Mockito.when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        Mockito.when(target.getUrl()).thenReturn("github.com/org");

        String result = service.getFullPackageName(target, "my-package");
        assertEquals("github.com/org#my-package", result);
    }

    @Test
    void returnsPackageNameWhenTypeIsNpmEvenIfUrlIsPresent() {
        PublishTargetInterface target = Mockito.mock(PublishTargetInterface.class);
        Mockito.when(target.getType()).thenReturn(PublishTargetType.NPM);
        Mockito.when(target.getUrl()).thenReturn("some-source");

        String result = service.getFullPackageName(target, "my-package");
        assertEquals("my-package", result);
    }

}
