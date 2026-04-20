#!/bin/bash
# ============================================================
# setup-kafka-native.sh
# Sets up Kafka natively using KRaft mode (no Docker, no Zookeeper)
# Run this ONCE before starting the application
# ============================================================

KAFKA_VERSION="3.6.1"
KAFKA_DIR="kafka_2.13-${KAFKA_VERSION}"
KAFKA_TGZ="${KAFKA_DIR}.tgz"
KAFKA_URL="https://downloads.apache.org/kafka/${KAFKA_VERSION}/${KAFKA_TGZ}"

echo "======================================================"
echo " Kafka Native Setup — KRaft Mode (No Zookeeper)"
echo "======================================================"

# Step 1: Download Kafka if not present
if [ ! -d "$KAFKA_DIR" ]; then
    echo ""
    echo "⬇️  Downloading Kafka ${KAFKA_VERSION}..."
    wget -q --show-progress "$KAFKA_URL" -O "$KAFKA_TGZ"
    echo "📦 Extracting..."
    tar -xzf "$KAFKA_TGZ"
    rm "$KAFKA_TGZ"
    echo "✅ Kafka extracted to ./${KAFKA_DIR}"
else
    echo "✅ Kafka already downloaded at ./${KAFKA_DIR}"
fi

cd "$KAFKA_DIR" || exit 1

# Step 2: Generate cluster ID and format storage
echo ""
echo "🔧 Configuring Kafka KRaft mode..."
KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
echo "   Cluster ID: $KAFKA_CLUSTER_ID"

bin/kafka-storage.sh format -t "$KAFKA_CLUSTER_ID" -c config/kraft/server.properties --ignore-formatted 2>/dev/null
echo "✅ Storage formatted."

echo ""
echo "======================================================"
echo " Setup complete!"
echo ""
echo " To START Kafka:"
echo "   cd ${KAFKA_DIR} && bin/kafka-server-start.sh config/kraft/server.properties"
echo ""
echo " To CREATE topics (run in a new terminal after Kafka starts):"
echo "   cd ${KAFKA_DIR}"
echo "   bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic financial-transactions --partitions 3 --replication-factor 1"
echo "   bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic transaction-alerts --partitions 3 --replication-factor 1"
echo "   bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --topic audit-log --partitions 1 --replication-factor 1"
echo ""
echo " To STOP Kafka:"
echo "   cd ${KAFKA_DIR} && bin/kafka-server-stop.sh"
echo "======================================================"
