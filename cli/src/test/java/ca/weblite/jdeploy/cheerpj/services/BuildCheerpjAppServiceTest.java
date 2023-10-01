package ca.weblite.jdeploy.cheerpj.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BuildCheerpjAppServiceTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void build() throws IOException {
        BuildCheerpjAppService service = new BuildCheerpjAppService();
        File dest = new File("/tmp/cheerpj/swingset2");
        dest.mkdirs();
        service.build(
                new File("/Users/shannah/.jdeploy/gh-packages/a1a64da2fae5f93e2ae203fff1d742f9.jdeploy-demo-swingset2/0.0.0-master/jdeploy-bundle/swingset2-1.0-SNAPSHOT.jar"),
                dest
        );
    }
}