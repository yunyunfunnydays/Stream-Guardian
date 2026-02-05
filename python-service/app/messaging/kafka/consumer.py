"""Kafka consumer implementation."""
import json
from typing import Callable, Awaitable

import structlog
from aiokafka import AIOKafkaConsumer

from app.messaging.base import MessageConsumer

logger = structlog.get_logger()


class KafkaMessageConsumer(MessageConsumer):
    """Kafka implementation of MessageConsumer."""

    def __init__(
        self,
        bootstrap_servers: str,
        topic: str,
        group_id: str,
    ):
        self.bootstrap_servers = bootstrap_servers
        self.topic = topic
        self.group_id = group_id
        self._consumer: AIOKafkaConsumer | None = None

    async def start(self) -> None:
        """Start the Kafka consumer."""
        self._consumer = AIOKafkaConsumer(
            self.topic,
            bootstrap_servers=self.bootstrap_servers,
            group_id=self.group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=True,
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        )
        await self._consumer.start()
        logger.info(
            "kafka_consumer_started",
            topic=self.topic,
            group_id=self.group_id,
        )

    async def stop(self) -> None:
        """Stop the Kafka consumer."""
        if self._consumer:
            await self._consumer.stop()
            logger.info("kafka_consumer_stopped")

    async def consume(self, handler: Callable[[dict], Awaitable[dict]]) -> None:
        """
        Consume messages and process them with the handler.

        Args:
            handler: Async function that processes a message and returns a result
        """
        if not self._consumer:
            raise RuntimeError("Consumer not started")

        async for msg in self._consumer:
            try:
                message_data = msg.value
                logger.info(
                    "kafka_message_received",
                    message_id=message_data.get("message_id"),
                    tenant_id=message_data.get("tenant_id"),
                    user_id=message_data.get("user_id"),
                )

                # Process with handler and get result
                result = await handler(message_data)

                # Yield result for producer to send
                yield {
                    "message_id": message_data.get("message_id"),
                    "tenant_id": message_data.get("tenant_id"),
                    "user_id": message_data.get("user_id"),
                    "original_text": message_data.get("text"),
                    **result,
                }

            except Exception as e:
                logger.error(
                    "kafka_message_processing_failed",
                    error=str(e),
                    message_id=msg.value.get("message_id") if msg.value else None,
                )
