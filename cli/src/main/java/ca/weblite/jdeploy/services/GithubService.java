package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.PackageInfoBuilder;

public class GithubService {

    private String token;

    public PackageInfoBuilder createPackageInfoBuilder(String source) {
        PackageInfoBuilder builder = new PackageInfoBuilder();
        builder.setSource(source);

        return builder;
    }


}
