@echo off
echo Déploiement du projet...


if not exist build mkdir build

echo Compilation des sources...
javac -d build src\*.java

if %errorlevel% neq 0 (
    echo Erreur de compilation des sources !
    exit /b 1
)

if not exist ..\Test\build mkdir ..\Test\build

echo Compilation des tests...
cd ..\Test
javac -cp .;..\Sprint2\build -d build *.java

if %errorlevel% neq 0 (
    echo Erreur de compilation des tests !
    cd ..\Sprint2
    exit /b 1
)

echo Exécution des tests...
java -cp build;..\Sprint2\build TestAnnotation

if %errorlevel% neq 0 (
    echo Erreur lors de l'exécution des tests !
    cd ..\Sprint2
    exit /b 1
)

cd ..\Sprint2
echo Déploiement et tests terminés avec succès !
pause