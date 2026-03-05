#!/bin/bash
echo "🔍 Compilation et exécution de l'exploration..."
scalac -classpath "$(find ~/.ivy2 -name 'spark-sql*.jar' | head -1):$(find ~/.ivy2 -name 'spark-core*.jar' | head -1)" src/spark/ExploreDataMart02.scala -d /tmp/spark-classes 2>/dev/null

if [ $? -eq 0 ]; then
  scala -classpath "/tmp/spark-classes:$(find ~/.ivy2 -name '*.jar' | tr '\n' ':')" ExploreDataMart02
else
  echo "❌ Erreur de compilation"
fi
