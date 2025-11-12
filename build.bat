@echo off
REM ==============================
REM Script général de compilation Java (Windows)
REM ==============================

REM Nom du fichier JAR à générer
set JAR_NAME=monprojet.jar

REM Dossiers
set SRC_DIR=src
set BUILD_DIR=classes
set LIB_DIR=lib
set DEST_DIR=C:\Program Files\Apache Software Foundation\Tomcat 10.0\webapps\url_test

echo === Compilation du projet et création de %JAR_NAME% ===

REM Nettoyer et recréer le dossier des classes
if exist "%BUILD_DIR%" (
    rmdir /s /q "%BUILD_DIR%"
)
mkdir "%BUILD_DIR%"

REM Construire le classpath à partir de toutes les libs du dossier lib\
set CP=.
if exist "%LIB_DIR%" (
    for %%f in ("%LIB_DIR%\*.jar") do (
        set CP=!CP!;%%f
    )
)

REM Activer l'expansion différée
setlocal enabledelayedexpansion

echo Compilation des fichiers Java...
dir /s /b "%SRC_DIR%\*.java" > sources.txt
javac -cp "!CP!" -d "%BUILD_DIR%" @sources.txt
del sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Erreur de compilation.
    exit /b 1
)

echo Création du JAR...
jar cf "%JAR_NAME%" -C "%BUILD_DIR%" .

echo ✅ Compilation terminée avec succès : %JAR_NAME%

copy /Y "%JAR_NAME%" "%DEST_DIR%\"
echo 📦 Copié dans : %DEST_DIR%\%JAR_NAME%

endlocal
pause
