#!/bin/bash
rm -rf ./target

# Step 1: Prepare the release (updates the version and creates a tag)
mvn release:prepare

# Step 2: Perform the release
mvn release:perform

# Step 3: Check out the release tag
RELEASE_TAG=$(git describe --tags --abbrev=0)
git checkout $RELEASE_TAG

# Step 4: Build the Docker image tar with the release version
mvn clean verify jib:buildTar