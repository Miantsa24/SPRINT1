@echo off
echo =====================================================
echo =   COMPILATION ET EMBALLAGE DU FRONT SERVLET       =
echo =====================================================

set SRC_DIR=src
set BUILD_DIR=build
set DIST_DIR=dist
set LIB_DIR=lib
set TEST_LIB_DIR=..\test\WEB-INF\lib
set SERVLET_JAR=%LIB_DIR%\servlet-api.jar
set OUTPUT_JAR=%DIST_DIR%\FrontServlet.jar

rem --- Nettoyage ---
echo Nettoyage des anciens fichiers...
if exist %BUILD_DIR% rmdir /s /q %BUILD_DIR%
if exist %DIST_DIR% rmdir /s /q %DIST_DIR%
mkdir %BUILD_DIR%
mkdir %DIST_DIR%

echo.
echo Compilation du code source...
javac -d %BUILD_DIR% -classpath "%SERVLET_JAR%" %SRC_DIR%\*.java

if %errorlevel% neq 0 (
    echo Erreur de compilation !
    pause
    exit /b 1
)

echo Compilation réussie !

echo.
echo Création du fichier JAR...
cd %BUILD_DIR%
jar -cvf ..\%OUTPUT_JAR% *
cd ..

echo JAR créé : %OUTPUT_JAR%

echo.
echo Copie du JAR vers test/WEB-INF/lib...
if not exist %TEST_LIB_DIR% mkdir %TEST_LIB_DIR%
copy %OUTPUT_JAR% %TEST_LIB_DIR% >nul

if %errorlevel% neq 0 (
    echo Erreur lors de la copie du JAR vers test/WEB-INF/lib !
    pause
    exit /b 1
)

echo Copie réussie : %OUTPUT_JAR% → %TEST_LIB_DIR%

echo.
echo Le projet est prêt pour être exécuté avec Tomcat.
pause
