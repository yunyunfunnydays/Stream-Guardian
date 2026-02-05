"""Message handling logic for Kafka consumer."""
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from app.messaging.kafka.consumer import KafkaMessageConsumer
    from app.messaging.kafka.producer import KafkaResultProducer


async def process_message(message_data: dict, graph) -> dict:
    """Process a single message through the LangGraph pipeline."""
    result = await graph.ainvoke({
        "tenant_id": message_data.get("tenant_id"),
        "user_id": message_data.get("user_id"),
        "username": message_data.get("username"),
        "text": message_data.get("text"),
        "timestamp": message_data.get("timestamp"),
        "messages": [],
    })
    return result


async def run_consumer(
    consumer: "KafkaMessageConsumer",
    producer: "KafkaResultProducer",
    graph,
) -> None:
    """Background task to consume messages from Kafka and send results."""
    async for result in consumer.consume(lambda msg: process_message(msg, graph)):
        await producer.send(result)
