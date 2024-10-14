package ca.weblite.jdeploy.cli.parsers;

import ca.weblite.jdeploy.cli.dtos.UpdateReleaseLinksRequest;

public class UpdateReleaseLinksRequestParser {
    public UpdateReleaseLinksRequest parse(String[] args) {
        return new UpdateReleaseLinksRequest(args);
    }
}
