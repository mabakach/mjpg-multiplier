#!/bin/bash
rm -rf  .\target
mvn release:prepare release:perform jib:buildTar
