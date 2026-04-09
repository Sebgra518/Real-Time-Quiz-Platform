#!/bin/bash

mkdir -p out
find src -name "*.java" > sources.txt
javac -cp "lib/*" -d out @sources.txt
java -cp "out:lib/*" MainClass