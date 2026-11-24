@echo off
set FOLDER=%~dp0
set JAR=%FOLDER%target\api-tester.jar
if not exist "%JAR%" (
  echo Building api-tester.jar for the first time...
  pushd "%FOLDER%"
  call mvn -q package -DskipTests
  popd
)
java -jar "%JAR%" %*
