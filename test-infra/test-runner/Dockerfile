FROM maven:3.9-eclipse-temurin-8

RUN apt-get update && apt-get install -y \
    curl \
    nodejs \
    npm \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy the full project
COPY . .

# Build shared library first, then CLI (skip tests during build)
RUN cd shared && mvn clean install -DskipTests -q \
    && cd ../cli && mvn clean package -DskipTests -q

# Default command: run the mock-network integration tests
CMD ["mvn", "-f", "cli/pom.xml", "test", \
     "-Dtest=ca.weblite.jdeploy.publishing.MockNetworkPublishingTest", \
     "-Dmock.network.tests=true", \
     "-Dfailsafe.useFile=false"]
