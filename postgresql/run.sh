#!/bin/bash

# Initialiser SDKMAN si disponible
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# Aller dans le répertoire du projet
cd "$(dirname "$0")"

# Vérifier si les fichiers sont compilés
if [ ! -d "bin" ]; then
    echo "Les fichiers ne sont pas encore compilés!"
    echo "Exécutez d'abord: ./compile.sh"
    exit 1
fi

# Exécuter le programme
cd bin
java -cp ".:../postgresql-42.7.1.jar" Main
