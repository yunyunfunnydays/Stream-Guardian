"""Base interfaces for messaging implementations."""
from abc import ABC, abstractmethod
from typing import AsyncIterator, Callable, Awaitable


class MessageConsumer(ABC):
    """Interface for consuming messages from a messaging system."""

    @abstractmethod
    async def start(self) -> None:
        """Start the consumer."""
        pass

    @abstractmethod
    async def stop(self) -> None:
        """Stop the consumer."""
        pass

    @abstractmethod
    async def consume(self, handler: Callable[[dict], Awaitable[dict]]) -> AsyncIterator[dict]:
        """
        Consume messages, process them with the handler, and yield results.

        Args:
            handler: Async function that processes a message and returns a result

        Yields:
            Processed result dict ready for sending
        """
        pass


class ResultProducer(ABC):
    """Interface for producing analysis results to a messaging system."""

    @abstractmethod
    async def start(self) -> None:
        """Start the producer."""
        pass

    @abstractmethod
    async def stop(self) -> None:
        """Stop the producer."""
        pass

    @abstractmethod
    async def send(self, result: dict) -> None:
        """
        Send an analysis result.

        Args:
            result: The analysis result to send
        """
        pass
