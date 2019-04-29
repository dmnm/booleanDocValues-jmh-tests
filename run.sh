#!/bin/sh -e

# requires Maven and JDK 11

mvn clean install && \
java -cp \
./target/benchmarks.jar:\
./libs/lucene-core-9.0.0-jmh-tests-SNAPSHOT.jar:\
./libs/lucene-codecs-9.0.0-jmh-tests-SNAPSHOT.jar:\
./libs/lucene-sandbox-9.0.0-jmh-tests-SNAPSHOT.jar: \
org.openjdk.jmh.Main -rf json

# with profiling
#org.openjdk.jmh.Main -rf json -prof perfasm

