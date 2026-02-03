"""LangGraph pipeline builder with MCP integration (2025 Native Tool Calling)."""
from functools import partial
import structlog
from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import ToolNode, tools_condition
from langchain_mcp_adapters.client import MultiServerMCPClient

from app.graph.state import ModerationState
from app.graph.nodes import screener_node, should_moderate, moderator_node, get_llm
from app.config import get_settings

logger = structlog.get_logger()


async def build_moderation_graph():
    """
    建構聊天審核 LangGraph Pipeline (with MCP tools)。

    流程:
    START -> screener -> [should_moderate] -> moderator -> tools -> moderator -> END
                              |                              ^         |
                              +-> END                        +---------+
                                                         (tools_condition loop)
    """
    settings = get_settings()

    # 1. 連接 MCP Server，動態取得工具
    mcp_client = MultiServerMCPClient({
        "stream-guardian": {
            "url": f"{settings.java_service_url}/mcp/sse",
            "transport": "sse",
        }
    })

    try:
        tools = await mcp_client.get_tools()
        logger.info("========== mcp_tools_loaded", tools=[t.name for t in tools])
    except Exception as e:
        logger.error("========== mcp_connection_failed", error=str(e))
        tools = []

    # 2. 建構 StateGraph
    graph = StateGraph(ModerationState)

    # 3. 加入節點
    graph.add_node(screener_node)
    graph.add_node("moderator", partial(moderator_node, tools=tools))
    graph.add_node("tools", ToolNode(tools))

    # 4. 定義邊
    graph.add_edge(START, "screener_node")

    # Screener 後的條件路由
    graph.add_conditional_edges(
        "screener_node",
        should_moderate,
        {"moderator": "moderator", "end": END}
    )

    # Moderator 後使用 tools_condition (Native Tool Calling pattern)
    graph.add_conditional_edges("moderator", tools_condition)

    # Tools 執行後回到 moderator (ReAct loop)
    graph.add_edge("tools", "moderator")

    return graph.compile(), mcp_client


# --- Graph Singleton ---

_compiled_graph = None
_mcp_client = None


async def get_moderation_graph():
    """Get or create the compiled moderation graph."""
    global _compiled_graph, _mcp_client
    if _compiled_graph is None:
        _compiled_graph, _mcp_client = await build_moderation_graph()
    return _compiled_graph


async def cleanup():
    """Cleanup MCP client connection."""
    global _mcp_client
    if _mcp_client:
        await _mcp_client.close()
        _mcp_client = None
