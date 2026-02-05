"""Configuration settings for Python service."""
from functools import lru_cache
from typing import Optional

from pydantic import HttpUrl, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # Service Info
    app_name: str = "stream-guardian-python"
    app_version: str = "1.0.0"
    debug: bool = False
    log_level: str = "INFO"

    # Java MCP Server Connection
    java_service_url: HttpUrl = "http://localhost:8080"
    mcp_sse_endpoint: str = "/mcp/sse"
    mcp_messages_endpoint: str = "/mcp/messages"

    # Qdrant Vector DB
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333

    # Kafka Messaging
    kafka_bootstrap_servers: str = "localhost:9094"
    kafka_inbound_topic: str = "chat.messages.inbound"
    kafka_results_topic: str = "chat.analysis.results"
    kafka_consumer_group: str = "stream-guardian-python"

    # AI Provider (SecretStr hides values in logs)
    anthropic_api_key: Optional[SecretStr] = None
    openai_api_key: Optional[SecretStr] = None
    google_api_key: Optional[SecretStr] = None
    llm_model: str = "gpt-5-mini"
    enable_ai_analysis: bool = True

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False
    )


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()
