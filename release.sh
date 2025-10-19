#!/bin/bash
rm -rf ./target

# Get the current branch name
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

# Step 1: Prepare the release (updates the version and creates a tag)
mvn release:prepare

# Step 2: Perform the release
mvn release:perform

# Step 3: Check out the release tag
RELEASE_TAG=$(git describe --tags --abbrev=0)
git checkout $RELEASE_TAG
# Clean up Maven release plugin backup files
rm -f pom.xml.releaseBackup release.properties

# Step 4: Build the Docker image tar with the release version
mvn clean verify jib:buildTar

# Step 5: Return to the original branch
git checkout $CURRENT_BRANCH

# Step 6: Commit and push pom.xml if modified
if [[ $(git status --porcelain pom.xml) ]]; then
  git add pom.xml
  git commit -m "Update pom.xml to next snapshot version after release"
  git push
fi