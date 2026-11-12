#!/bin/bash
# Compila MatraMotors (coches, barcos y trenes) y genera el .jar en build/
set -e
cd "$(dirname "$0")/.."
rm -rf build/clases && mkdir -p build/clases
JAVA_TOOL_OPTIONS= javac -nowarn --release 21 -g:source,lines -encoding UTF-8 -cp "$(ls lib21/*.jar | tr '\n' ':')" \
  -d build/clases $(find src -name '*.java')
cp res/matracar/plugin.yml res/matracar/config.yml res/matracar/config-trenes.yml build/clases/
VER=$(grep '^version:' res/matracar/plugin.yml | awk '{print $2}')
(cd build/clases && jar --create --file ../MatraMotors-$VER.jar .)
echo "build/MatraMotors-$VER.jar"
