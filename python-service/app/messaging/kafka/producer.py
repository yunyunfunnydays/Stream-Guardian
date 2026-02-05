"""Kafka producer implementation."""
import json

import structlog
from aiokafka import AIOKafkaProducer

from app.messaging.base import ResultProducer

logger = structlog.get_logger()


class KafkaResultProducer(ResultProducer):
    """Kafka implementation of ResultProducer."""

    def __init__(
        self,
        bootstrap_servers: str,
        topic: str,
    ):
        self.bootstrap_servers = bootstrap_servers
        self.topic = topic
        self._producer: AIOKafkaProducer | None = None

    async def start(self) -> None:
        """Start the Kafka producer."""
        self._producer = AIOKafkaProducer(
            bootstrap_servers=self.bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )
        await self._producer.start()
        logger.info("kafka_producer_started", topic=self.topic)

    async def stop(self) -> None:
        """Stop the Kafka producer."""
        if self._producer:
            await self._producer.stop()
            logger.info("kafka_producer_stopped")

    async def send(self, result: dict) -> None:
        """
        Send an analysis result to Kafka.

        Args:
            result: The analysis result to send
        """
        if not self._producer:
            raise RuntimeError("Producer not started")

        # Partition key for ordering
        partition_key = f"{result.get('tenant_id')}:{result.get('user_id')}"

        await self._producer.send(
            self.topic,
            value=result,
            key=partition_key.encode("utf-8"),
        )

        logger.info(
            "kafka_result_sent",
            message_id=result.get("message_id"),
            tenant_id=result.get("tenant_id"),
            is_flagged=result.get("is_flagged"),
        )
