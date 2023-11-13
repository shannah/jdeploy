package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.repositories.JDeployProjectRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class JDeployProjectCache {
    private final JDeployProjectRepository repository;

    private final Map<String, JDeployProject> cache = new HashMap<>();

    @Inject
    public JDeployProjectCache(JDeployProjectRepository repository) {
        this.repository = repository;
    }

    public JDeployProject findByPath(String path) throws IOException {
        if (cache.containsKey(getCacheKey(path))) {
            return cache.get(getCacheKey(path));
        }
        JDeployProject project = repository.findByPath(path);
        cache.put(getCacheKey(path), project);

        return project;
    }

    public void clearCache() {
        cache.clear();
    }

    private String getCacheKey(String path) {
        return new File(path).getAbsolutePath();
    }

}
