#!/bin/bash

# Initialiser SDKMAN
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Aller dans le répertoire du projet
cd "$(dirname "$0")"

echo "Compilation du projet..."

# Créer le répertoire bin pour les fichiers compilés
mkdir -p bin

# Compiler tous les fichiers Java
javac -cp ".:../postgresql-42.7.1.jar" -d bin \
    src/model/*.java \
    src/dao/*.java \
    src/connexion.java \
    src/Main.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation réussie!"
    echo ""
    echo "Pour exécuter le programme:"
    echo "  cd bin"
    echo "  java -cp \".:../../postgresql-42.7.1.jar\" Main"
else
    echo "✗ Erreur de compilation!"
    exit 1
fi
