"""LangGraph state definitions for chat moderation pipeline."""
from typing import Optional, Literal, List, Any, Annotated
from typing_extensions import TypedDict
from langgraph.graph import add_messages
from langchain_core.messages import BaseMessage


class ModerationState(TypedDict, total=False):
    """State for chat moderation pipeline with native tool calling."""

    # --- Message History (for LLM tool calling) ---
    messages: Annotated[list[BaseMessage], add_messages]

    # --- Input Context ---
    tenant_id: str
    user_id: str
    username: Optional[str]
    text: str
    timestamp: Optional[int]

    # --- Screener Output ---
    is_flagged: bool
    flag_reasons: List[str]
    confidence: float

    # --- Final Result ---
    action_taken: Optional[str]
    action_result: Optional[dict[str, Any]]

    # --- Metadata ---
    model_name: Optional[str]
    error: Optional[str]
