@echo off
if not exist out mkdir out
if exist sources.txt del sources.txt
for /r src %%f in (*.java) do echo %%f>>sources.txt
for /r model %%f in (*.java) do echo %%f>>sources.txt
javac -cp "lib/*" -d out @sources.txt
java -cp "out;lib/*" MainClass
pause