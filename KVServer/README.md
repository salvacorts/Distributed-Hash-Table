# CPEN 431 - Assigment 4

**Name:** Salvador Corts Sánchez

**UBC Student ID:** 91291682

### Usage
We use maven to build and run this project although a compiled *jar* is available in the base directory of the project.

#### Run precompiled *jar*
`java -jar A5.jar <port_to_use>`

#### Build and run
Compile with maven:

`mvn install`

*<u>**IMPORTANT:**</u> Maven will execute some test launching a server listening on port **2019***.

It will create two *jar* files in the ***target/*** directory:
 1. ***Client-jar-with-dependencies.jar***: Client to test the server manually.
 2. ***Server-jar-with-dependencies.jar***: Server

To run them use:

- **Server:** `java -jar target/Server-jar-with-dependencies.jar <kvstore_port> <metrics_port>` 

    E.g. `java -jar target/Server-jar-with-dependencies.jar 2019`


- **Client:** `java -jar target/Client-jar-with-dependencies.jar <server_address> <server_port> <command_code> <key> <value> <version>` for the client. 

    E.g. `java -jar target/Client-jar-with-dependencies.jar 127.0.0.1 2019 1 foo bar 1`, 

   or java -jar target/Client-jar-with-dependencies.jar 127.0.0.1 2019 4 '' '' ''`

### Design
It is a multi-threaded server which runs a worker thread for each request. All the workers share an static concurrent-safe Set which stores UUIDs being processed at a moment, so if a message arrives duplicated it is only processed once. Each time a worker finished it updates the cache the server's average processing time used to return the *overloadWaitTime* parameter when there is an overload in the server.

The cache is used in order to not to process the same request twice. We use [guava's cache](https://github.com/google/guava/wiki/CachesExplained).

A separate metric server is hosted for exporting CPU, memory usage, and total # of keys in this node
The CPU and memory are updated on a separate thread every 5 seconds
Keys are updated upon each successful put/delete/clear request