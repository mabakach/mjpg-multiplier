#!/bin/bash
$JAVA_HOME=/Users/mbaumgar/.jenv/shims/java
rm -rf  .\target
mvn release:prepare release:perform jib:buildTar
