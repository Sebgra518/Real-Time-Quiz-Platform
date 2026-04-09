@echo off
mkdir out 2>nul
dir /s /b src\*.java > sources.txt
javac -cp "lib/*" -d out @sources.txt
java -cp "out;lib/*" MainClass
pause