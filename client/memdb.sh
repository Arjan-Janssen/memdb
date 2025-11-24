#!/usr/bin/env bash
java -jar $(dirname "$0")/app/build/libs/app-uber.jar ${@:1}
