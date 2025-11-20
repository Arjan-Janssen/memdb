Memdb
=====

# Description

**Memdb** is a tool for debugging heap allocations. It consists of a server and a client part.

The server is a lightweight component that can be easily linked to an existing project. It starts a web socket to report realtime statistics about dynamic memory allocations and deallocations.

The client is a command line tool that can connect to the open socket and captures the stream of heap operations from the socket connection.

The captured heap operations can then be further analyzed in the client tool. There are several commands to investigate the behaviour of the heap over time.

# Getting Started

## Dependencies

* JVM 21

## Installation

JVM 21 should be installed

## Executing program

## Troubleshooting

## Authors

** Developer: ** * Arjan Janssen *
Contact: arjan.janssen@gmail.com

## Version History

* 0.0
** Work in progress towards initial release

## License

This project is licensed under the BSD License - see LICENSE.md for details.

## Acknowledgements

I started this project to familiarize myself with new programming languages Rust and Kotlin while simultaneously building an open-source tool for tracking down memory issues.

This project was inspired by a tool called Heap Inspector. I was impressed by its features and wanted to build a similar tool myself.
