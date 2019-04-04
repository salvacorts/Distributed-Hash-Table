# CPEN 431 Assignment 12

## Group ID

9A

## Group Members

Michael Moore - 30400345

Matthew Chernoff - 11530145

Salvador Corts - 91291682

## Verification code

5B26AB99E6327F479CED364A07F1DD66

## Usage

java -Xmx64m -jar A11.jar 10145 1234 4321 nodes-list.txt

The first argument is port number, the second is metrics port, the third is epidemic port.
Note that nodes-list.txt is in a different format than servers.txt

## Description

To correct for routing failure when RESUMING nodes, we changed our replication protocol.
Instead of chain replication, a node receiving a request multicasts it to all 3 replicated nodes,
so the key copies can be recovered after a node resumption. 


## Servers

EC2: 

54.200.80.137:10145

Planetlab: 

See servers.txt