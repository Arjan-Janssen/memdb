#!/usr/bin/env bash

# Basic integration test to verify that server waits for the client to connect and terminates
# after a connection is established.

# build and run server
pushd ../server
cargo build --example grow_vec
echo "Running server:"
./target/debug/examples/grow_vec &
popd

# Wait until the server is up on the default port
{
  while ! echo -n > /dev/tcp/localhost/8989; do
    sleep 1s
  done
} 2>/dev/null

normal=$(tput sgr0)
green=$(tput setaf 2)
red=$(tput setaf 1)

# build and run client
pushd ../client
./gradlew build
./gradlew uberJar
popd
$(dirname "$0")/../client/memdb.sh --capture localhost:8989
if [ "$?" -eq 0 ]; then
    printf 'client %sPASSED%s\n' $green $normal
else
    printf 'server %sFAILED%s\n' $red $normal
fi

echo "Waiting for server to close..."
wait $!
if [ "$?" -eq 0 ]; then
    printf 'server %sPASSED%s\n' $green $normal
else
    printf 'server %sFAILED%s\n' $red $normal
fi
exit $?