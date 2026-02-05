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

    # 只提取可 JSON 序列化的欄位，排除 LangChain 的 AIMessage 物件
    return {
        "is_flagged": result.get("is_flagged", False),
        "flag_reasons": result.get("flag_reasons", []),
        "confidence": result.get("confidence", 0.0),
        "action_taken": result.get("action_taken"),
        "action_result": result.get("action_result"),
        "model_name": result.get("model_name"),
    }


async def run_consumer(
    consumer: "KafkaMessageConsumer",
    producer: "KafkaResultProducer",
    graph,
) -> None:
    """Background task to consume messages from Kafka and send results."""
    async for result in consumer.consume(lambda msg: process_message(msg, graph)):
        await producer.send(result)
