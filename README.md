# CPEN 431. Distributed Hash Table

Implementation of a Distributed Hash Table to store a map of key value pairs on a peer-to-peer system.

## Authors

Michael Moore, Salvador Corts, Matthew Chernoff

## Usage

java -Xmx64m -jar A11.jar 10145 1234 4321 nodes-list.txt

The first argument is port number, the second is metrics port, the third is epidemic port.
Note that nodes-list.txt is in a different format than servers.txt

## Description

To correct for routing failure when RESUMING nodes, we changed our replication protocol.
Instead of chain replication, a node receiving a request multicasts it to all 3 replicated nodes,
so the key copies can be recovered after a node resumption.
