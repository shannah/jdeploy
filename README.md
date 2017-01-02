# jDeploy

A tool to deploy Java applications using NPM

![jDeploy flow](https://raw.githubusercontent.com/wiki/shannah/jdeploy/images/jdeploy-graphic.png)

## Requirements

* NodeJS
* Java 8 (Only required for publishing with jDeploy.  Installing/using deployed apps do not require Java to be installed.  The app will automatically install a JRE if java is not already installed).

Runs on any platform that supports requirements including Mac, Windows, and Linux.

## Features

* **Jar files** - Publish Java executable jar files to npm
* **War files** - Publish war files to npm
* **Web Apps** - Publish web apps (exploded war files) to npm
* **Self-contained web apps** - Web apps are wrapped in a self-contained app with embedded Jetty server.
* **Simple Installation** - Apps deployed using jDeploy can be installed using a single command: `npm install -g <your-app>`
* **No Java Dependencies** - Java not required to install and run apps that are deployed using jDeploy.  The app will automatically download a JRE at runtime if the system doesn't already have Java.
* **Easy versioning and updates** - Deploying updates through NPM is trivial.

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

* [Introduction to jDeploy (Screencast)](https://youtu.be/j0qZ9akmcCQ) [Slides](https://docs.google.com/presentation/d/1ZOrUnbACtiEmZHBiq6wqW4afieUctulK-QyODJ5FuGY/pub?start=true&loop=false&delayms=5000)
* [Getting Started with jDeploy Tutorial](https://github.com/shannah/jdeploy/wiki/Getting-Started-with-JDeploy)
* [package.json Reference](https://github.com/shannah/jdeploy/wiki/package.json-reference)
* [jDeploy Demo Projects](https://github.com/shannah/jdeploy-demos)

## License

[ISC](http://www.isc.org/downloads/software-support-policy/isc-license/)

## Contact

[Steve Hannah](http://sjhannah.com)


