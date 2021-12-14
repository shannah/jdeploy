## Changes

### Version 2.0.10

December 14, 2021

- Fixed issue with downloading JDK on each run

### Version 2.0.9

December 14, 2021

- Changed to use AdoptOpenJDK instead of Oracle's JDK
- Added support for `javaVersion` property in the `jdeploy` section of the _package.json_ file to lock into specific JRE version.  Supported value 8/9/10/11/12/13.