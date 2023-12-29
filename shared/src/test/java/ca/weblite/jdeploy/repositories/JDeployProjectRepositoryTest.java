package ca.weblite.jdeploy.repositories;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.models.JDeployProject;
import org.codejargon.feather.Feather;
import org.codejargon.feather.Provides;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.inject.Provider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JDeployProjectRepositoryTest {

    private Feather feather;
    @Mock
    private FileSystemInterface fileSystemInterface;

    @InjectMocks
    private JDeployProjectRepository repository;

    private class Module {
        @Provides
        public FileSystemInterface fileSystemInterface() {
            return fileSystemInterface;
        }
    }

    @BeforeEach
    void setUp() {
        feather = Feather.with(new Module());
        repository = feather.instance(JDeployProjectRepository.class);
    }

    @Test
    public void testFindByPath() throws IOException {
        Path path = Paths.get("test");
        Path packageJSON = Paths.get("test", "package.json");

        when(fileSystemInterface.isDirectory(path)).thenReturn(true);
        when(fileSystemInterface.exists(packageJSON)).thenReturn(true);
        when(fileSystemInterface.readToString(packageJSON, StandardCharsets.UTF_8)).thenReturn("{\"name\": \"test\"}");

        JDeployProject project = repository.findByPath("test");
        assertEquals(packageJSON, project.getPackageJSONFile());
        assertEquals("test", project.getPackageJSON().getString("name"));
    }

    @Test
    public void findByPathShouldThrowNotFoundIfDirectoryDoesntExist() {
        Path path = Paths.get("test");
        when(fileSystemInterface.isDirectory(path)).thenReturn(false);
        assertThrows(FileNotFoundException.class, () -> {
            repository.findByPath("test");
        });
    }

    @Test
    public void findByPathShouldThrowNotFoundIfPackageJSONDoesntExist() {
        Path path = Paths.get("test");
        Path packageJSON = Paths.get("test", "package.json");

        when(fileSystemInterface.isDirectory(path)).thenReturn(true);
        when(fileSystemInterface.exists(packageJSON)).thenReturn(false);
        assertThrows(FileNotFoundException.class, () -> {
            repository.findByPath("test");
        });
    }
}