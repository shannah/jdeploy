# jDeploy

Developer friendly desktop deployment tool.  See [the jDeploy Website](https://www.jdeploy.com) for usage instructions.

## GitHub Action Instructions

The jdeploy github action allows you to generate native desktop installers for your Java project in a Github workflow.  This action can be run for both commits and releases.  

If used on a commit to a branch, it will publish the the app installers to a release named after the branch. E.g. For the "master" branch, it would post installers to a tag named "master".  If used on a "tag", it will simply add the installers as artifacts of the release.  In both cases it will add some download links to the release notes.

### Example Workflow:

```yaml
# This workflow will build a Java project with Maven and bundle them as native app installers with jDeploy
# See https://www.jdeploy.com for more information.

name: jDeploy CI with Maven

on:
  push:
    branches: ['*']
    tags: ['*']

jobs:
  build:

    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      - name: Build with Maven
        run: mvn -B package --file pom.xml
      - name: Build App Installer Bundles
        uses: actions/jdeploy@master
        with:
          github_token: ${{ secrets.STEVE_PAT }}
```

See https://www.jdeploy.com/docs/manual/#_publishing_on_github[the jDeploy Developer Guide] to learn more.

### Supported Parameters

| Parameter           | Description                                                                            | Default                   |
|---------------------|----------------------------------------------------------------------------------------|---------------------------|
| `github_token`      | GitHub Action token, e.g. `"${{ secrets.GITHUB_TOKEN }}"`.                             | `null`                    |
| `target_repository` | The repository where releases should be published to, if different than the current repo. | `${{ github.repository }}` |
| `deploy_target`     | The deployment target. "github" or "npm"                                               | `github`                  |
| `npm_token`         | The `NPM_TOKEN` for publishing to npm.  Only required if `deploy_target`==`npm`        | `null`                    |`
| `jdeploy_version`   | The jdeploy version to use for building the installers.                                | `4.0.0-alpha.38`           |

## License

[Apache2](LICENSE)

## Contact

[Steve Hannah](http://sjhannah.com)


