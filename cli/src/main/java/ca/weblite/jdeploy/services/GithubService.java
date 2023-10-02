package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.PackageInfoBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class GithubService {

    private String token;

    public PackageInfoBuilder createPackageInfoBuilder(String source) {
        PackageInfoBuilder builder = new PackageInfoBuilder();
        builder.setSource(source);

        return builder;
    }

    public String getRepoURL(File repoDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("git", "config", "--get", "remote.origin.url")
                .directory(repoDir);
        Process process = builder.start();
        Scanner scanner = new Scanner(process.getInputStream());
        String url = scanner.nextLine();
        scanner.close();
        return url;
    }

}
