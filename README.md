# CPEN 431 Assignment 11

## Group ID

9A

## Group Members

Michael Moore - 30400345

Matthew Chernoff - 11530145

Salvador Corts - 91291682

## Verification code

10953614938F08F77A4A03FD0D934B4E

## Usage

java -Xmx64m -jar A8.jar 10145 1234 4321 nodes-list.txt

The first argument is port number, the second is metrics port, the third is epidemic port.
Note that nodes-list.txt is in a different format than servers.txt

## Description

To implement data replication, we added an optional reps (repititions) field to the KeyValueRequest protobuf object.
Upon receiving a PUT or DELETE request, a node will set the reps field to 2 and then forward it to the 
next node in the hash circle. This results in 3 nodes having a copy of the key-value.

Because GET requests are automatically routed to subsequent nodes in the hash circle, this results in successful 
data retrieval when some nodes are down.

We also added another rejoin protocol - each reactivated node will individually test every node to see if it is
alive, without starting epidemics or relying on others.

## Servers

EC2: 

ec2-54-188-206-157.us-west-2.compute.amazonaws.com:10145

Planetlab: 

See servers.txt