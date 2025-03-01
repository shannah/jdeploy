package ca.weblite.jdeploy.cli.services;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;

import java.util.Scanner;

public class CLIOneTimePasswordProvider implements OneTimePasswordProviderInterface {
    @Override
    public String promptForOneTimePassword(PublishingContext context, PublishTargetInterface target) {
        context.out().println("Please enter One Time Password for " + target.getName() + ": ");
        return new Scanner(context.in()).nextLine().trim();
    }
}
