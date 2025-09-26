@echo off
REM 
set SRC=src\FrontServlet.java
set BUILD=build
set LIB=lib\servlet-api.jar
set OUT=dist\FrontServlet.jar

REM
if exist %BUILD% rmdir /s /q %BUILD%
if exist dist rmdir /s /q dist
mkdir %BUILD%
mkdir dist

REM -
echo Compilation de %SRC% ...
javac -cp %LIB% -d %BUILD% %SRC%
if errorlevel 1 (
    echo Erreur de compilation !
    exit /b 1
)

REM 
echo Création du JAR %OUT% ...
jar cvf %OUT% -C %BUILD% .

echo Build terminé avec succès !
pause
