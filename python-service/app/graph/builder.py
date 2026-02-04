"""LangGraph pipeline builder with MCP integration (2025 Native Tool Calling)."""
from functools import partial
from typing import Optional, List
import structlog
from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import ToolNode, tools_condition
from langchain_core.tools import BaseTool

from app.graph.state import ModerationState
from app.graph.nodes import screener_node, should_moderate, moderator_node, get_llm

logger = structlog.get_logger()


async def build_moderation_graph(tools: List[BaseTool]):
    """
    建構聊天審核 LangGraph Pipeline (with MCP tools)。

    流程:
    START -> screener -> [should_moderate] -> moderator -> tools -> moderator -> END
                              |                              ^         |
                              +-> END                        +---------+
                                                         (tools_condition loop)
    """
    # 1. 使用從 MCP session 載入的工具
    logger.info("========== building_graph_with_tools", tool_names=[t.name for t in tools])

    # 2. 建構 StateGraph 加入狀態定義
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

    return graph.compile()


# --- Graph Singleton ---

_compiled_graph = None


async def get_moderation_graph(tools: Optional[List[BaseTool]] = None):
    """Get or create the compiled moderation graph."""

    global _compiled_graph
    if _compiled_graph is None:
        if tools is None:
            raise RuntimeError("Tools required for first graph initialization")
        _compiled_graph = await build_moderation_graph(tools)
        logger.info("========== graph_initialized")
    logger.debug("========== get_moderation_graph")
    return _compiled_graph


async def cleanup():
    """Cleanup graph cache."""
    global _compiled_graph
    _compiled_graph = None
