# jdeploy

A tool to deploy Java applications using NPM

## Requirements

* NodeJS
* Java 8 (Only required for publishing with jDeploy.  Installing/using deployed apps do not require Java to be installed.  The app will automatically install a JRE if java is not already installed).

Runs on any platform that supports requirements including Mac, Windows, and Linux.

## Installation

**Windows**

~~~~
$ npm install jdeploy -g
~~~~

**Mac/Linux**

~~~~
$ sudo npm install jdeploy -g
~~~~

## Usage

In terminal, navigate to a directory containing an executable .jar file or a .war file that you would like to publish.

~~~~
$ jdeploy init
~~~~

This will generate a package.json file with settings to allow you to publish the app to npm.

**Install app locally on your machine.**

~~~~
$ jdeploy install
~~~~

**Publish App to NPM**

~~~~
$ jdeploy publish
~~~~


## Documentation

* Introduction to jDeploy (Screencast)
* [Getting Started with jDeploy Tutorial](https://github.com/shannah/jdeploy/wiki/Getting-Started-with-JDeploy)
* [package.json Reference](https://github.com/shannah/jdeploy/wiki/package.json-reference)

## License

[ISC](http://www.isc.org/downloads/software-support-policy/isc-license/)

## Contact

[Steve Hannah](http://sjhannah.com)


