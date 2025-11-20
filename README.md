Memdb
=====

# Description

**Memdb** is a multi-platform tool for debugging heap allocations. It consists of a server and a client part.

The server is a lightweight component that can be easily linked to an existing project. It starts a web socket to report realtime statistics about dynamic memory allocations and deallocations. The server is built in the Rust programming language.

The client is a command line tool that can connect to the open socket and captures the stream of heap operations from the socket connection. The client is a JVM application written in Kotlin.

The captured heap operations can then be further analyzed in the client tool. There are several commands to investigate the behaviour of the heap over time.

# Getting Started

## Dependencies

* JVM 21
* Google Protobufs

## Installation

* Install JVM 21
* Install Kotlin (2.2.21)
* Install Rust (1.91.1)

Building the client can be done by invoking the gradle wrapper with the build command:

```bash
 ./gradlew build
```

## Instrumenting the application

The first step is to instrument the application that you want to debug. This works by importing the server library in your application and using the instrumented allocator.

## Executing program

memdbg --capture [ip]:[port]

Where ip is the IP address where the server application is running. This can be localhost when client and server run on the same machine, but it can also be the IP address of another system, such as a mobile device or game console.

## Troubleshooting

## Authors

** Developer: ** * Arjan Janssen *
Contact: arjan.janssen@gmail.com

## Version History

* 0.0
** Work in progress towards initial release

## License

This project is licensed under the FreeBSD License - see LICENSE.md for details.

## Acknowledgements

I started this project to familiarize myself with new programming languages Rust and Kotlin while simultaneously building an open-source tool for tracking down memory issues.

This project was inspired by a tool called Heap Inspector. It was used by one of my team members to track down memory leaks for a console game many years ago. I wanted to build a memory debugging tool myself to more easily track down problems with heap allocations.
