package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.DIContext;

import ca.weblite.jdeploy.records.PackageJsonValidationResult;
import ca.weblite.jdeploy.services.*;
import java.io.File;
import java.util.*;

public class ProjectInitializerCLIController extends BaseController implements Runnable {

    private final ProjectInitializer.Context context;
    private final JavaVersionExtractor javaVersionExtractor;
    private final ProjectInitializer projectInitializer;
    private int resultCode = 0;

    private boolean noPrompt;
    private boolean generateGithubWorkflow;

    public ProjectInitializerCLIController(
            File packageJSONFile,
            String[] args,
            boolean noPrompt,
            boolean generateGithubWorkflow
    ) {
        super(packageJSONFile, args);
        this.noPrompt = noPrompt;
        this.generateGithubWorkflow = generateGithubWorkflow;
        this.javaVersionExtractor = DIContext.get(JavaVersionExtractor.class);
        this.projectInitializer = DIContext.get(ProjectInitializer.class);
        this.context =  new ProjectInitializer.ContextBuilder()
                .setDirectory(packageJSONFile.getParentFile())
                .setGenerateGithubWorkflow(true)
                .setGeneratePackageJson(true)
                .setFixPackageJson(true)
                .build();
    }

    public int getResultCode() {
        return resultCode;
    }

    @Override
    public void run() {
        try {
            ProjectInitializer.ContextBuilder contextBuilder = new ProjectInitializer.ContextBuilder();
            contextBuilder.setDirectory(context.getDirectory());
            ProjectInitializer.PreparationResult preparationResult = projectInitializer.prepare(this.context);
            contextBuilder.setGeneratePackageJson(true);
            if (preparationResult.isPackageJsonExists()) {
                contextBuilder.setGeneratePackageJson(false);
                if (!preparationResult.isPackageJsonIsValid()) {
                    out.println("A package.json file already exists, but some fields are missing or invalid:");
                    for (PackageJsonValidationResult.PackageJsonPropertyError error: preparationResult.getPackageJsonValidationResult().getErrors()) {
                        out.println("  - " + error.getMessage());
                    }

                    out.println("Proposed fixed package.json contents are as follows:");
                    out.println(preparationResult.getProposedPackageJsonContent().toString(2));
                    out.println("");

                    out.println("Would you like to fix the errors in package.json file? (y/N)");
                    Scanner scanner = new Scanner(System.in);
                    String response = scanner.next();
                    if ("y".equals(response.toLowerCase())) {
                        contextBuilder.setFixPackageJson(true);
                    } else {
                        contextBuilder.setFixPackageJson(false);
                    }
                } else {
                    contextBuilder.setFixPackageJson(false);
                }
            } else {
                out.println("No package.json file exists in this directory.  Would you like to create one? (y/N)");
                Scanner scanner = new Scanner(System.in);
                String response = scanner.next();
                if (!"y".equals(response.toLowerCase())) {
                    out.println("Cancelling project initialization");
                    return;
                }
            }

            if (!preparationResult.isGithubWorkflowExists()) {
                out.println("Would you like to generate a GitHub workflow file for this project (y/N)");
                Scanner scanner = new Scanner(System.in);
                String response = scanner.next();
                if ("y".equals(response.toLowerCase())) {
                    contextBuilder.setGenerateGithubWorkflow(true);
                } else {
                    contextBuilder.setGenerateGithubWorkflow(false);
                }
            } else {
                contextBuilder.setGenerateGithubWorkflow(false);
            }

            out.println("Initializing project...");
            ProjectInitializer.Context initContext = contextBuilder.build();
            ProjectInitializer.InitializeProjectResult result = projectInitializer.initializeProject(initContext);
            for (String message : result.getMessages()) {
                out.println(message);
            }
            out.println("Project initialization complete!");
            resultCode = 0;

        } catch (Exception ex) {
            getErr().println("Error initializing project: " + ex.getMessage());
            ex.printStackTrace(getErr());
            resultCode = 1;
        }
    }

}
