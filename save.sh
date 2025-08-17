#!/usr/bin/env bash

set -e

./mvnw spring-javaformat:apply

git commit -am up

if [ -z "$1" ]; then
  git push
else
  git push origin "$1"
fi