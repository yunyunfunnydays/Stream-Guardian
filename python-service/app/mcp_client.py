"""MCP Client for communicating with Java MCP Server."""
from typing import Any
import httpx
import structlog
from tenacity import retry, stop_after_attempt, wait_exponential

from app.config import get_settings
from app.models import JsonRpcRequest

logger = structlog.get_logger()


class McpClient:
    """Client for MCP Server using HTTP POST (JSON-RPC over HTTP)."""

    def __init__(self, base_url: str | None = None, session_id: str | None = None):
        settings = get_settings()
        self.base_url = str(base_url or settings.java_service_url)
        self.messages_endpoint = settings.mcp_messages_endpoint
        self.session_id = session_id
        self._request_id = 0
        self._initialized = False
        self._client = httpx.AsyncClient(timeout=30.0)

    def _next_id(self) -> int:
        self._request_id += 1
        return self._request_id

    def _build_url(self) -> str:
        url = f"{self.base_url}{self.messages_endpoint}"
        if self.session_id:
            url += f"?sessionId={self.session_id}"
        return url

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
    async def _send_request(self, method: str, params: dict | None = None) -> dict:
        """Send a JSON-RPC request to the MCP server."""
        request = JsonRpcRequest(
            id=self._next_id(),
            method=method,
            params=params
        )

        logger.info("mcp_request", method=method, params=params)

        response = await self._client.post(
            self._build_url(),
            json=request.model_dump(exclude_none=True),
            headers={"Content-Type": "application/json"}
        )
        response.raise_for_status()

        result = response.json()
        logger.info("mcp_response", result=result)

        if "error" in result and result["error"]:
            raise Exception(f"MCP Error: {result['error']}")

        return result.get("result", {})

    async def initialize(self) -> dict:
        """Initialize MCP connection."""
        result = await self._send_request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "stream-guardian-python",
                "version": "1.0.0"
            }
        })
        self._initialized = True
        await self._send_request("initialized", {})
        return result

    async def list_tools(self) -> list[dict]:
        """List available tools from MCP server."""
        result = await self._send_request("tools/list", {})
        return result.get("tools", [])

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict:
        """Call a tool on the MCP server."""
        return await self._send_request("tools/call", {
            "name": name,
            "arguments": arguments
        })

    async def aclose(self):
        """Close the async HTTP client."""
        await self._client.aclose()


# Global singleton
_mcp_client: McpClient | None = None


def get_mcp_client() -> McpClient:
    """Get or create the MCP client singleton."""
    global _mcp_client
    if _mcp_client is None:
        _mcp_client = McpClient()
    return _mcp_client
