# CPEN 431 Assignment 9

## Group ID

9A

## Group Members

Michael Moore - 30400345

Matthew Chernoff - 11530145

Salvador Corts - 91291682

## Verification code

E0DA2E2352DF4293346E659E57444AF4

## Usage

java -Xmx64m -jar A8.jar 10145 1234 4321 nodes-list.txt

The first argument is port number, the second is metrics port, the third is epidemic port.
Note that nodes-list.txt is in a different format than servers.txt

## Description

We reworked our key distribution method for this assignment by assigning each Node a hash value (or several)
and giving it control of each key below than or equal to its hash value, and greater than the last Node's
hash value in the circle.

Upon a node leaving, its hash values are removed from the circle and thus every key that it would've received
is taken care of by the next node in the circle. Upon rejoining, its hash values are readded to the circle.

## Servers

EC2: 

ec2-54-188-206-157.us-west-2.compute.amazonaws.com:10145

Planetlab: 

See servers.txt