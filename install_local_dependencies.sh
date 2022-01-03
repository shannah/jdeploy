#!/bin/bash
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/XMLLib.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=xmllib -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -DlocalRepositoryPath=maven-repository
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/CN1-Compatlib.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=cn1-compatlib -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -DlocalRepositoryPath=maven-repository
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/zip4j-2.3.1-SNAPSHOT.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=zip4j -Dversion=2.3.1-SNAPSHOT \
                         -Dpackaging=jar -DlocalRepositoryPath=maven-repository
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/jetty-runner.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=jetty-runner -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -DlocalRepositoryPath=maven-repository
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/image4j-0.7.2.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=image4j -Dversion=0.7.2 \
                         -Dpackaging=jar -DlocalRepositoryPath=maven-repository
