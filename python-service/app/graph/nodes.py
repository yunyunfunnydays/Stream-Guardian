"""LangGraph nodes for chat moderation pipeline (2025 Native Tool Calling)."""
from typing import Literal
import structlog
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_anthropic import ChatAnthropic
from langchain_openai import ChatOpenAI
from langchain_google_genai import ChatGoogleGenerativeAI
from pydantic import BaseModel, Field

from app.graph.state import ModerationState
from app.config import get_settings

logger = structlog.get_logger()


# --- Pydantic schema for Screener structured output ---

class ScreenerOutput(BaseModel):
    """Screener AI 輸出結構"""
    is_flagged: bool = Field(description="是否違規")
    flag_reasons: list[str] = Field(default_factory=list, description="違規類型列表")
    confidence: float = Field(ge=0.0, le=1.0, description="信心分數 0-1")


# --- LLM Factory ---

def get_llm():
    """根據設定取得 LLM 實例 (優先順序: Google > Anthropic > OpenAI)"""
    settings = get_settings()

    if settings.google_api_key:
        return ChatGoogleGenerativeAI(
            model=settings.llm_model,
            google_api_key=settings.google_api_key.get_secret_value(),
            temperature=0
        )
    elif settings.anthropic_api_key:
        return ChatAnthropic(
            model=settings.llm_model,
            api_key=settings.anthropic_api_key.get_secret_value(),
            temperature=0
        )
    elif settings.openai_api_key:
        return ChatOpenAI(
            model=settings.llm_model,
            api_key=settings.openai_api_key.get_secret_value(),
            temperature=0
        )
    else:
        logger.warning("========== no_api_key_configured")
        return None


# --- Screener Node (Structured Output) ---

SCREENER_SYSTEM_PROMPT = """你是直播聊天室審核 AI。分析訊息是否違反以下規則：
- spam: 廣告、推銷、釣魚連結
- hate_speech: 仇恨言論、歧視、威脅
- harassment: 騷擾、人身攻擊、霸凌
- inappropriate: 色情、暴力、不當內容

若無違規，is_flagged 設為 false。"""


async def screener_node(state: ModerationState) -> dict:
    """Screener Node: 使用 Structured Output 檢測違規"""
    llm = get_llm()

    if llm is None:
        logger.info("========== screener_skipped", reason="no_llm_configured")
        return {"is_flagged": False, "flag_reasons": [], "confidence": 0.0}

    # 使用 with_structured_output (2024/2025 主流)
    structured_llm = llm.with_structured_output(ScreenerOutput)

    user_content = f"頻道: {state['tenant_id']}\n使用者: {state['user_id']}\n訊息: {state['text']}"

    try:
        result: ScreenerOutput = await structured_llm.ainvoke([
            SystemMessage(content=SCREENER_SYSTEM_PROMPT),
            HumanMessage(content=user_content)
        ])

        logger.info(
            "========== screener_result",
            tenant_id=state["tenant_id"],
            user_id=state["user_id"],
            is_flagged=result.is_flagged,
            flag_reasons=result.flag_reasons,
            confidence=result.confidence
        )

        return {
            "is_flagged": result.is_flagged,
            "flag_reasons": result.flag_reasons,
            "confidence": result.confidence,
            "model_name": getattr(llm, 'model', None)
        }

    except Exception as e:
        logger.error("========== screener_error", error=str(e))
        return {"is_flagged": False, "flag_reasons": [], "confidence": 0.0, "error": str(e)}


# --- Router Condition ---

def should_moderate(state: ModerationState) -> Literal["moderator", "end"]:
    """決定是否需要進入審核流程"""
    if state.get("is_flagged") and state.get("confidence", 0) > 0.5:
        return "moderator"
    return "end"


# --- Moderator Node (Native Tool Calling) ---

MODERATOR_SYSTEM_PROMPT = """你是直播聊天室管理 AI。根據違規審核結果，使用適當的工具執行管理動作。

決策原則：
- 嚴重違規 (hate_speech, 威脅): 使用 ban_user
- 中度違規 (harassment): 使用 timeout_user (duration: 300)
- 輕度違規 (spam, inappropriate): 使用 reply_chat 發送警告

重要規則：
- 只執行一個工具，然後停止
- 如果工具已經成功執行，不要再呼叫任何工具
- 看到工具執行成功的結果後，直接回覆確認即可"""


async def moderator_node(state: ModerationState, tools: list) -> dict:
    """
    Moderator Node: 使用 Native Tool Calling 選擇並呼叫 MCP 工具。
    tools 由 builder 注入 (從 MCP Server 動態取得)
    """
    llm = get_llm()

    if llm is None:
        logger.info("========== moderator_skipped", reason="no_llm_configured")
        return {}

    # Native Tool Calling: bind_tools
    llm_with_tools = llm.bind_tools(tools)

    # 檢查是否已有 messages (表示是 ReAct loop 的後續呼叫)
    existing_messages = state.get("messages", [])

    if existing_messages:
        # 後續呼叫：使用現有 messages (包含 tool 執行結果)
        messages = existing_messages
        logger.info("========== moderator_continuing", message_count=len(messages))
    else:
        # 首次呼叫：建立初始 messages
        user_content = f"""審核結果：
- 違規類型: {state.get('flag_reasons', [])}
- 信心分數: {state.get('confidence', 0.0)}
- 原始訊息: {state['text']}
- 頻道 ID: {state['tenant_id']}
- 使用者 ID: {state['user_id']}
- 使用者名稱: {state.get('username', state['user_id'])}"""

        messages = [
            SystemMessage(content=MODERATOR_SYSTEM_PROMPT),
            HumanMessage(content=user_content)
        ]
        logger.info("========== moderator_first_call")

    response = await llm_with_tools.ainvoke(messages)

    logger.info(
        "========== moderator_response",
        tool_calls=response.tool_calls if hasattr(response, 'tool_calls') else None
    )

    # 回傳 messages 供 ToolNode 處理
    return {"messages": [response]}
