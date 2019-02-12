# CPEN 431 Assignment 5 

## Group ID

9A

## Group Members

Michael Moore - 30400345

Matthew Chernoff - 11530145

Salvador Corts - 91291682

## Monitoring

Two monitoring dashboards
KVStore Monitor: CPU/Memory usage, and number of keys stored on each KVStore instance
Server Monitor: Performance of Planetlab nodes

Node monitoring service (Grafana) hosted on:

http://ec2-54-188-206-157.us-west-2.compute.amazonaws.com:3000
credentials guest:guest

Prometheus database hosted on:
http://ec2-54-188-206-157.us-west-2.compute.amazonaws.com:9090/graph
 
## 3rd Party Technologies Used

Grafana

Prometheus

## Design Choices

Our monitoring system is based on Prometheus and Grafana, which store and display time series data, respectively
Prometheus gathers data from our Planetlab nodes. Each node runs an instance of node_exporter, Prometheus' system monitoring program
Statistics about the performance and status of our KVStore application is exported through a Prometheus java client
This Java client updates CPU/memory usage of the KVStore process every five seconds, and updates the number of keys upon every successful put/delete/clear request

We use a single script, nodes/node_setup/setup.sh to run an instance of node_exporter and KVstore

## Other Implementations


