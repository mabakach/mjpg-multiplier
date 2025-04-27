#!/bin/bash
rm -rf  .\target

mvn release:prepare

# Step 2: Rebuild the project and create the Docker image tar with the updated version
mvn clean verify jib:buildTar

# Step 3: Perform the release
mvn release:perform
