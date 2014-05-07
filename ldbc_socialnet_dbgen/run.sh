#!/bin/bash
HADOOP_HOME=/home/user/hadoop-1.2.1 #change to your hadoop folder
LDBC_SOCIALNET_DBGEN_HOME=/home/user/ldbc_socialnet_bm/ldbc_socialnet_dbgen #change to your ldbc_socialnet_dbgen folder 
NUM_MACHINES=1 #the number of threads to use.
OUTPUT_DIR=/home/user #change to the folder where the generated data should be written

export HADOOP_HOME
export LDBC_SOCIALNET_DBGEN_HOME
export NUM_MACHINES 
export OUTPUT_DIR 

# FOR INTERNAL USAGE
# WARNING: This folders are removed upon execution start. Be careful not to point to anything important
export HADOOP_TMP_DIR=$OUTPUT_DIR/hadoop
export DATA_OUTPUT_DIR=$OUTPUT_DIR/social_network

mvn clean
mvn assembly:assembly

cp $LDBC_SOCIALNET_DBGEN_HOME/target/ldbc_socialnet_dbgen.jar $LDBC_SOCIALNET_DBGEN_HOME/
rm $LDBC_SOCIALNET_DBGEN_HOME/target/ldbc_socialnet_dbgen.jar

$HADOOP_HOME/bin/hadoop jar $LDBC_SOCIALNET_DBGEN_HOME/ldbc_socialnet_dbgen.jar $HADOOP_TMP_DIR $NUM_MACHINES $LDBC_SOCIALNET_DBGEN_HOME $DATA_OUTPUT_DIR
