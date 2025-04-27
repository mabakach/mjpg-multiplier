Remove-Item .\target -Recurse -Force
mvn release:prepare jib:buildTar release:perform