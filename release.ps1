Remove-Item .\target -Recurse -Force

# Step 1: Prepare the release (updates the version and creates a tag)
mvn release:prepare

# Step 2: Check out the release tag
RELEASE_TAG=$(git describe --tags --abbrev=0)
git checkout $RELEASE_TAG

# Step 3: Build the Docker image tar with the release version
mvn clean verify jib:buildTar

# Step 4: Switch back to the main branch and perform the release
git checkout main
mvn release:perform
