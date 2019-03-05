# CPEN 431 Assignment 8

## Group ID

9A

## Group Members

Michael Moore - 30400345

Matthew Chernoff - 11530145

Salvador Corts - 91291682

## Verification code

AD7929938415F0EA11BFA9DC699C2687

## Usage

java -Xmx64m -jar A8.jar 10145 1234 4321 nodes-list.txt

The first argument is port number, the second is metrics port, the third is epidemic port.
Note that nodes-list.txt is in a different format than servers.txt

## Description

The rejoin protocol is implemented by the program listening for a CONT signal. 
Once received, a server will spread an epidemic to other servers to signal that it is back online.

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
