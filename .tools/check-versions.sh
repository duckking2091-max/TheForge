#!/bin/bash
# Compile the plugin's main code against each Paper 1.21.x API.
# Usage: .tools/check-versions.sh   (needs Maven and internet access to repo.papermc.io)
unset JAVA_TOOL_OPTIONS; export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Djavax.net.ssl.trustStoreType=JKS"
for v in ${VERSIONS:-1.21 1.21.1 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11}; do
  rm -rf ${TMPDIR:-/tmp}/theforge-version-check && mkdir -p ${TMPDIR:-/tmp}/theforge-version-check && cp -r $(cd "$(dirname "$0")/.." && pwd)/pom.xml $(cd "$(dirname "$0")/.." && pwd)/src ${TMPDIR:-/tmp}/theforge-version-check/
  sed -i "s#<artifactId>paper-api</artifactId><version>[^<]*</version>#<artifactId>paper-api</artifactId><version>$v-R0.1-SNAPSHOT</version>#" ${TMPDIR:-/tmp}/theforge-version-check/pom.xml
  out=$(cd ${TMPDIR:-/tmp}/theforge-version-check && mvn -B -DskipTests -Dmaven.test.skip=true compile 2>&1)
  if echo "$out" | grep -q "BUILD SUCCESS"; then echo "$v: OK"; else echo "$v: FAILED"; echo "$out" | grep -A1 "ERROR.*\.java" | sed 's#${TMPDIR:-/tmp}/theforge-version-check/src/main/java/dev/duckmc/blastforge/##' | grep -v "^--" | head -12; echo "$out" | grep -m2 "Could not\|Caused"; fi
done
