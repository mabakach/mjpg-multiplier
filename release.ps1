Remove-Item .\target -Recurse -Force
mvn release:prepare release:perform jib:buildTar