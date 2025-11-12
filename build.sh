#!/bin/bash

# ==============================
# Script général de compilation Java
# ==============================

# Nom du fichier JAR à générer
JAR_NAME="monprojet.jar"

# Dossiers
SRC_DIR="src"
BUILD_DIR="classes"
LIB_DIR="lib"
DEST_DIR="C:\Program Files\Apache Software Foundation\Tomcat 10.0\webapps\url_test"

echo "=== Compilation du projet et création de $JAR_NAME ==="

# Nettoyer et recréer le dossier des classes
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Construire le classpath à partir de toutes les libs du dossier lib/
CP="."
if [ -d "$LIB_DIR" ]; then
  for jar in "$LIB_DIR"/*.jar; do
    CP="$CP:$jar"
  done
fi

# Compiler tous les fichiers .java du dossier src/
echo "Compilation des fichiers Java..."
find "$SRC_DIR" -name "*.java" > sources.txt
javac -cp "$CP" -d "$BUILD_DIR" @sources.txt
rm sources.txt

# Vérifier la compilation
if [ $? -ne 0 ]; then
  echo "❌ Erreur de compilation."
  exit 1
fi

# Créer le JAR (sans Main-Class)
echo "Création du JAR..."
jar cf "$JAR_NAME" -C "$BUILD_DIR" .

echo "✅ Compilation terminée avec succès : $JAR_NAME"


cp "$JAR_NAME" "$DEST_DIR/"
echo "📦 Copié dans : $DEST_DIR/$JAR_NAME"
