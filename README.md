# CPEN 431 Assignment 7

## Group ID

9A

## Group Members

Michael Moore - 30400345

Matthew Chernoff - 11530145

Salvador Corts - 91291682

## Verification code

0157315B3B4D4595D134D75941913B80

## Shutdown protocol

In src\main\java\com\g9A\CPEN431\A7\server\kvMap\RequestProcessor.java

Line 243: System.exit(0)

## Usage

java -Xmx64m -jar A7.jar 10145 1234 4321 servers.txt

The first argument is port number, the second is metrics port, the third is epidemic port

## Description

The epidemic protocol is implemented by two separate threads that run in the same process:

FailureCheck (src\main\java\com\g9A\CPEN431\A7\server\network\FailureCheck.java)

This thread periodically sends an "IsAlive" request to a random node, then sleeps for 5 seconds.
If the request times out, it assumes the node is down and pushes a new Epidemic thread to EpidemicServer.
Initially it waits 2 minutes to allow all servers to come online before pinging them.

EpidemicServer (src\main\java\com\g9A\CPEN431\A7\server\network\EpidemicServer.java)

This thread creates a separate port for receiving epidemic messages from other servers. 
When it receives an epidemic message, or if FailureCheck thread creates a new epidemic, it checks the 
cache to see if it has received the same epidemic before. If not, it spawns an epidemic thread.

Epidemic (src\main\java\com\g9A\CPEN431\A7\server\network\Epidemic.java)

An epidemic thread is created by EpidemicServer. It periodically sends a DeadNodeRequest 
to other servers and then sleeps for 5 seconds. The number of iterations is equal to the 
amount of alive nodes. It generates an ID based on the CRC32 hash of the server/port that is down,
which is put into a cache and checked by EpidemicServer to avoid recursive epidemics.

A new protocol buffer "DeadNodeRequest.proto" was created to send epidemic messages.
It contains the server address/port of the dead node and an operation code,
so it may possibly be used for other epidemic types in the future.

Another optional field "epId" (epidemic ID) was added to Message.proto to avoid recursive epidemics

## Servers

EC2: 

ec2-54-188-206-157.us-west-2.compute.amazonaws.com:10145


Planetlab:

planetlab1.cs.ubc.ca:10145

planetlab2.cs.ubc.ca:10145

planetlab2.cs.unc.edu:10145

planetlab4.mini.pw.edu.pl:10145

planetlab2.pop-pa.rnp.br:10145

plink.cs.uwaterloo.ca:10145

node1.planetlab.albany.edu:10145

pl1.eng.monash.edu.au:10145

pl2.eng.monash.edu.au:10145

planetlab2.inf.ethz.ch:10145

planetlab02.cs.washington.edu:10145

planetlab04.cs.washington.edu:10145

planetlab3.comp.nus.edu.sg:10145

pl1.sos.info.hiroshima-cu.ac.jp:10145

planetlab1.dtc.umn.edu:10145

pl1.rcc.uottawa.ca:10145

planetlab3.cesnet.cz:10145

node1.planetlab.mathcs.emory.edu:10145

plab1.cs.msu.ru:10145

planetlab1.cs.uoregon.edu:10145
