#!/bin/bash
# Compila Matrains y genera el .jar en build/
set -e
cd "$(dirname "$0")/.."
rm -rf build/clases-matrains && mkdir -p build/clases-matrains
JAVA_TOOL_OPTIONS= javac -nowarn --release 21 -g:source,lines -encoding UTF-8 -cp "$(ls lib21/*.jar | tr '\n' ':')" \
  -d build/clases-matrains $(find src/matrains -name '*.java')
cp res/matrains/plugin.yml res/matrains/config.yml build/clases-matrains/
VER=$(grep '^version:' res/matrains/plugin.yml | awk '{print $2}')
(cd build/clases-matrains && jar --create --file ../Matrains-$VER.jar .)
echo "build/Matrains-$VER.jar"
