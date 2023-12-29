package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.repositories.JDeployProjectRepository;
import org.codejargon.feather.Feather;
import org.codejargon.feather.Provides;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JDeployProjectCacheTest {

    private Feather feather;

    @Mock
    private JDeployProjectRepository repository;

    @InjectMocks
    private JDeployProjectCache cache;

    private class Module {
        @Provides
        public JDeployProjectRepository repository() {
            return repository;
        }
    }

    @BeforeEach
    void setUp() {
        feather = Feather.with(new Module());
    }

    @Test
    public void findByPath() throws IOException {
        String pathString = "test";
        Path path = Paths.get(pathString);
        Path packageJSONPath = Paths.get(pathString, "package.json");
        JSONObject packageJSON = new JSONObject("{\"name\": \"test\"}");
        when(repository.findByPath(pathString)).thenReturn(new JDeployProject(packageJSONPath, packageJSON));
        JDeployProject project = cache.findByPath(pathString);
        assertEquals(packageJSONPath, project.getPackageJSONFile());
        assertEquals("test", project.getPackageJSON().getString("name"));
        verify(repository, times(1)).findByPath(pathString);

        // check that repository.findByPath() is called only once
        project = cache.findByPath(pathString);
        assertEquals(packageJSONPath, project.getPackageJSONFile());
        assertEquals("test", project.getPackageJSON().getString("name"));
        verify(repository, times(1)).findByPath(pathString);

        cache.clearCache();

        // check that repository.findByPath() is called again after clearing the cache
        project = cache.findByPath(pathString);
        assertEquals(packageJSONPath, project.getPackageJSONFile());
        assertEquals("test", project.getPackageJSON().getString("name"));
        verify(repository, times(2)).findByPath(pathString);
    }

    @Test
    public void findByPathShouldThrowNotFoundExceptionWhenDirectoryDoesntExist() throws IOException {
        String pathString = "test";
        Path path = Paths.get(pathString);
        when(repository.findByPath(pathString)).thenThrow(new FileNotFoundException());
        assertThrows(FileNotFoundException.class, () -> {
            cache.findByPath(pathString);
        });
    }
}

