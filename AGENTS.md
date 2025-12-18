# Coding Style Guide (concise)

This guide captures the conventions and non-obvious patterns used across this codebase. It focuses on project-specific and uncommon conventions rather than generic Java best practices.

---

## Project layout & build systems
- Multi-module Java project:
  - `cli` is built with Maven (traditional `pom.xml`).
  - IntelliJ plugin uses Gradle Kotlin DSL (`build.gradle.kts`) and the `org.jetbrains.intellij` Gradle plugin.
- Gradle IntelliJ plugin configuration is declared in Kotlin DSL. Set JVM compatibility in `tasks.withType<JavaCompile>` and Kotlin target via `KotlinCompile.kotlinOptions.jvmTarget`.
- Use environment variables for secret values in Gradle tasks (e.g. `System.getenv("PUBLISH_TOKEN")`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`).

---

## Dependency Injection
- Use `javax.inject.Inject` + `javax.inject.Singleton` for constructor injection and singleton scopes.
- Central DI entrypoint: `DIContext.getInstance()` used to fetch singletons in places where constructor injection is not available (tests, builders).
- Constructors are used to declare dependencies; fields for injected dependencies are `private final`.

---

## Builders & Fluent Params pattern
- Builders implement an interface (e.g. `ProjectGeneratorRequest.Params`) and return that interface from setters for fluent chaining.
- Public getters on builders lazily derive values from multiple inputs (e.g. `magicArg`, `projectName`, `githubRepository`) rather than requiring callers to populate every field.
- Builders may embed CLI metadata as annotations on fields (see Command Line Parser section).

Example pattern:
- setX(...) returns `ProjectGeneratorRequest.Params` so callers can chain.
- getX() returns either explicitly-set value or an inferred default computed from other fields.

---

## Command-line metadata on fields
- Use a custom `CommandLineParser` annotation set on builder fields:
  - `@CommandLineParser.Alias("x")` for short flags
  - `@CommandLineParser.Help("...")` to document flags
  - `@CommandLineParser.PositionalArg(n)` for positional args
- These annotations are placed on private fields rather than on methods.

---

## Template + replacement conventions
- Template placeholders use Mustache-like double-brace tokens: `{{ foo }}` (e.g. `{{ packagePath }}`, `{{ mainClass }}`).
- Replacement occurs for many text file types (explicit list in code): .java, .properties, .xml, .gradle, .json, .yml, .yaml, .md, .adoc.
- File names are also processed for placeholder replacements (not only file contents).
- Placeholder substitution is performed by sequential String#replace calls (simple token replacement, not a template engine).
- `packagePath` placeholder is expected to be a `/`-separated path (derived from `packageName.replace(".", "/")`).

---

## File & directory handling
- Use Apache Commons IO `FileUtils` for copying, moving, reading/writing files and directories.
- When creating project directories:
  - Create with `mkdirs()` and then verify existence; throw `IOException` if creation failed.
- When iterating template files:
  - Copy directories with `FileUtils.copyDirectory(...)` and files with `FileUtils.copyFileToDirectory(...)`.
- When processing files recursively:
  - Recurse into directories, process files, then apply rename/move operations (so moved/renamed entries are returned by helper methods).

---

## Extension merging
- Project extensions are applied by a `ProjectDirectoryExtensionMerger` (merge extension directories into project directory). Treat extensions as additive overlays on a template.

---

## Naming & derived defaults
- Many values are computed from inputs in the following precedence order:
  - explicit value set on builder
  - derived from `githubRepository` if provided (e.g. `groupId` → `com.github.[user]`)
  - derived from `magicArg` when it contains `package.Class` form
  - fallbacks such as `my-app`, `My App`, `com.example.myapp`
- Use helper `StringUtils` for:
  - camelCase ↔ lower-case-with-separator conversions
  - `ucFirst`, `ucWords`, `lowerCaseWithSeparatorToCamelCase`
  - `isValidJavaClassName`, `countCharInstances`

Naming conventions used by helpers:
- artifactId/project-name → lower case with `-` separator
- class names → CamelCase; package names → dot-separated lowercase

---

## GitHub & git conventions
- GitHub repo operations:
  - Creating repository via REST `POST https://api.github.com/user/repos` using `HttpURLConnection` and a JSON body (`{"name": "...", "private": true/false}`).
  - Use a token from `GithubTokenService` in `Authorization: Bearer <token>`.
- Git operations use JGit:
  - Initialize repo with `Git.init().setDirectory(localPath).call()`.
  - Manage `origin` remote via JGit `RemoteConfig` and `URIish`, checking for existing URIs and adding if absent.
  - Commit with `git.add().addFilepattern(".").call()` and `git.commit().setMessage(...).call()`.
  - Push with `git.push().setRemote("origin").setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))`. (token passed as username and empty password)
- When dealing with releases for private repos:
  - Create a "-releases" repository, populate a README template and initialize/publish it.
  - In workflows, replace `${{ secrets.GITHUB_TOKEN }}` with `${{ secrets.JDEPLOY_RELEASES_TOKEN }}` and insert a `target_repository` field under the same indentation level—indentation must be preserved. Use scanner-based method to compute indentation of a line.

---

## Constants & string literals
- Use `private static final` constants for repeated external strings (e.g. `GITHUB_URL`, `JDEPLOY_TOKEN_SECRET_NAME`, `GITHUB_API_URL`).
- Prefer `String.valueOf(...)` when adding potentially null values to templates to avoid NPEs in replacement code.

---

## Error handling & checked exceptions
- Validate early: throw checked `Exception` or `IOException` for invalid inputs like missing template directory or existing project directory.
- Wrap IO exceptions into unchecked `RuntimeException` in helper methods where appropriate (e.g. workflow file modification).
- Methods interacting with external services (HTTP, Git) propagate checked exceptions to caller for higher-level handling/tests.

---

## Tests & test utilities
- Use JUnit 5 (`@BeforeEach`, `@AfterEach`, `Assumptions`, `@Disabled`).
- Create temporary directories with `Files.createTempDirectory(...)` and delete them in `@AfterEach` via `FileUtils.deleteDirectory`.
- Tests use `DIContext` to obtain instances; tests also use `MavenBuilder` to build generated projects in integration-style tests.
- Tests may introspect private fields using reflection to assert builder-derived defaults (helper method sets `setAccessible(true)`).
- Use `Assumptions.assumeTrue(getJavaVersion() >= 17, "...")` when a test requires a certain Java runtime.
- Use `@DisabledOnOs` / OS conditions for platform-specific tests where needed.

---

## Use of Java features and libraries worth noting
- javax.inject for DI (lighter-weight than Spring-style annotations).
- JGit (org.eclipse.jgit) for programmatic git operations — remote management via `RemoteConfig` and `URIish`.
- Direct use of `HttpURLConnection` for simple GitHub REST calls (no heavy HTTP client).
- Use of `org.apache.commons.io.FileUtils` and `ca.weblite.tools.io.IOUtil` for file I/O helpers.
- Project templates handled as filesystem directories with placeholders; no templating engine is used—simple string replacement suffices for current needs.

---

## Gradle Kotlin DSL & IntelliJ plugin specifics
- Configure the IntelliJ plugin block with:
  - `version.set("...")`, `type.set("IC")`, and `plugins.set(listOf("git4idea"))`.
- Use `patchPluginXml` to set `sinceBuild` / `untilBuild`.
- Use `signPlugin` and `publishPlugin` tasks, sourcing credentials from environment variables (avoid hard-coding secrets).
- Keep Kotlin and Java target jvm versions consistent (17 in this project).

---

## Miscellaneous conventions
- Console output used for progress info in CLI and initializers (e.g., `System.out.println` for successful repository creation).
- Tests include both unit and integration-style tests that run external build tools (Maven) where appropriate.
- When searching/processing text files to insert YAML fields, preserve existing indentation by computing indent of the target line and inserting new lines with the same indent.

---

If you want, I can:
- Convert these conventions into a checklist for PR reviews.
- Produce linting rules / code templates (e.g., a builder skeleton) matching these patterns.