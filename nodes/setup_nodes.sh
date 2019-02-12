if ! [ -x "$(command -v pssh)" ]; then
   echo "Install pssh"
   exit 1
fi

if [ "$#" -ne 1 ]; then
    echo "Missing keys parameter"
    exit 1
fi

keys=$1

# Create our folder in every node
pssh -h nodes.list -l ubc_cpen431_9 -x "-oStrictHostKeyChecking=no -i $keys" -i "mkdir -p 9A"

# Copy our setup files
pscp -h nodes.list -l ubc_cpen431_9 -x "-oStrictHostKeyChecking=no -i $keys" -r ./node_setup/ ~/9A/

# Run setup script
pssh -h nodes.list -l ubc_cpen431_9 -x "-oStrictHostKeyChecking=no -i $keys" -i "bash ~/9A/node_setup/setup.sh"