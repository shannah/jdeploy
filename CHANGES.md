## Changes

### Version TBD

TBD

- Added singleton application support: Enable `singleton: true` in jDeploy config to ensure only one instance of your application runs at a time. When a user opens a file or URI while the app is already running, it will be forwarded to the existing instance instead of launching a new one. Configure via the "Singleton Application" checkbox in the Java Runtime panel.

### Version 2.0.10

December 14, 2021

- Fixed issue with downloading JDK on each run

### Version 2.0.9

December 14, 2021

- Changed to use AdoptOpenJDK instead of Oracle's JDK
- Added support for `javaVersion` property in the `jdeploy` section of the _package.json_ file to lock into specific JRE version.  Supported value 8/9/10/11/12/13.