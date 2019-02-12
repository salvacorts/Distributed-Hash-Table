source ./config.env
mkdir -p ./install

# install java
if ! [ -x "$(command -v java)" ]; then
   wget --no-check-certificate -O jre.rpm http://javadl.sun.com/webapps/download/AutoDL?BundleId=101397
   sudo rpm -ivh jre.rpm
   rm jre.rpm
fi

# Extract prometheus node exporter
tar -xf ./resources/node_exporter.tar.gz -C ./install --strip-components=1

# Run node exporter (run as daemon) and the KVStore service
./install/node_exporter $PROM_ARGS &
java -jar $kvstore_jvm_args ./resources/kvstore.jar $kvstore_listening_port $kvstore_metrics_port &


# Add a cron job to run node exporter and kvstore on every restart
crontab -l | { cat; echo "@reboot $(pwd)/install/node_exporter $PROM_ARGS &"; } | crontab -
crontab -l | { cat; echo "@reboot java -jar $kvstore_jvm_args $(pwd)/resources/kvstore.jar $kvstore_listening_port $kvstore_metrics_port &"; } | crontab -

