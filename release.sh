#!/bin/bash
rm -rf  .\target
mvn release:prepare jib:buildTar release:perform
