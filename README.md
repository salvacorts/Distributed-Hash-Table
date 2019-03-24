# CPEN 431 Assignment 11

## Group ID

9A

## Group Members

Michael Moore - 30400345

Matthew Chernoff - 11530145

Salvador Corts - 91291682

## Verification code

7719BCD44A462C7310F048FEFDAD389B

## Usage

java -Xmx64m -jar A11.jar 10145 1234 4321 nodes-list.txt

The first argument is port number, the second is metrics port, the third is epidemic port.
Note that nodes-list.txt is in a different format than servers.txt

## Description

Sequential consistency is ensured by WRITES and READS always targeting the same node, with replication
only used for failures.

## Servers

EC2: 

54.213.99.225:10145

Planetlab: 

See servers.txt