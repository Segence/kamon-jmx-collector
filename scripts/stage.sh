#!/usr/bin/env bash

set -e

function usage {
    echo "Usage: $0 <dependencies|generate-tag|unit-test|integration-test|publish-local|publish|publish-to-bintray> [PROJECT]"
    exit 1
}

function dependencies {
    ./gradlew clean dependencies
}

function generate-tag {
    git tag --force $(./gradlew printVersion | sed -n '2p')
}

function unit-test {
    ./gradlew clean test
}

function integration-test {

    if [ -f $(pwd)/src/integTest/docker-compose.yml ]; then

        if [[ -z "${DOCKER_BIND_HOST}" ]]; then
            if [ "$(uname -s | cut -c1-5)" == "Linux" ]; then
                export DOCKER_BIND_HOST=$(ip addr | grep 'eth0:' -A2 | tail -n1 | awk '{print $2}' | cut -f1  -d'/')
            elif [ "$(uname -s | cut -c1-6)" == "Darwin" ]; then
                export DOCKER_BIND_HOST=$(ifconfig en0 | grep inet | grep -v inet6 | awk '{print $2}')
            else
                echo "Unknown operating system, can not determine IP address."
                exit 1
            fi
        fi

        echo -e "\nDocker bind host: $DOCKER_BIND_HOST\n"

        trap 'docker-compose -f ./src/integTest/docker-compose.yml down' INT TERM EXIT

        docker-compose -f ./src/integTest/docker-compose.yml up -d

        if grep -q kafka $(pwd)/src/integTest/docker-compose.yml; then
            sleep 60
            docker-compose -f ./src/integTest/docker-compose.yml exec -T broker kafka-topics --create --topic test --partitions 3 --replication-factor 1 --if-not-exists --zookeeper zookeeper:2181
        fi

        sleep 10
    fi

    ./gradlew clean integrationTest
}

function publish-local {
    ./gradlew clean test publishToMavenLocal
}

function publish {
    ./gradlew clean test publish
}

function publish-to-bintray {
    ./gradlew clean test bintrayUpload
}

CURRENT_DIR=`dirname "$0"`
cd $CURRENT_DIR/..

GOAL=$1

case $GOAL in
  dependencies)
    dependencies
    ;;
  generate-tag)
    generate-tag
    ;;
  unit-test)
    unit-test
    ;;
  integration-test)
    integration-test
    ;;
  publish-local)
    publish-local
    ;;
  publish)
    publish
    ;;
  publish-to-bintray)
    publish-to-bintray
    ;;
  *)
    usage
    ;;
esac
