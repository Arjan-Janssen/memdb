#!/usr/bin/env bash
java -jar $(dirname "$0")/build/libs/com.janssen.memdb-uber.jar ${@:1}
