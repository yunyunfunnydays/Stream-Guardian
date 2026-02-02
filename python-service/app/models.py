"""Data models for Stream Guardian Python service."""
from typing import Optional, Literal, List, Any, Union
from pydantic import BaseModel, Field, field_validator, ConfigDict


class ChatMessage(BaseModel):
    """從 Java Ingest 服務傳入的原始聊天訊息。"""
    model_config = ConfigDict(populate_by_name=True)

    tenant_id: str = Field(..., description="租戶或頻道 ID", min_length=1)
    user_id: str = Field(..., description="使用者唯一識別碼", min_length=1)
    username: Optional[str] = Field(None, description="顯示名稱")
    text: str = Field(..., description="訊息全文內容", min_length=1)
    timestamp: Optional[int] = Field(None, description="Unix 時間戳 (ms)，建議為 UTC")


class AnalysisResult(BaseModel):
    """AI 分析管線的最終輸出結果。"""
    tenant_id: str
    user_id: str
    original_text: str

    # --- 審核結果 (Screening) ---
    is_flagged: bool = Field(False, description="是否偵測到違規")
    flag_reasons: List[str] = Field(default_factory=list, description="違規標籤清單")
    confidence: float = Field(
        0.0,
        ge=0.0,
        le=1.0,
        description="AI 對此判斷的信心分數 (0-1)"
    )

    # --- 決策路徑 (Routing) ---
    intent: Optional[Literal["ban", "timeout", "warn", "reply", "ignore"]] = Field(
        None,
        description="系統建議採取的行動"
    )

    # --- 執行追蹤 (Action Tracking) ---
    action_taken: Optional[str] = Field(None, description="最終執行的動作說明")
    action_result: Optional[dict] = Field(None, description="工具執行後的原始回傳資料")

    # --- 觀察與調校 (Observability) ---
    model_name: Optional[str] = Field(None, description="產生此結果的 AI 模型名稱")


# --- MCP (Model Context Protocol) 協議區塊 ---

class McpToolCall(BaseModel):
    """AI 決定呼叫工具的結構。"""
    name: str = Field(..., description="欲呼叫的工具名稱")
    arguments: dict[str, Any] = Field(default_factory=dict, description="工具參數")


class JsonRpcRequest(BaseModel):
    """符合 JSON-RPC 2.0 規範的請求，用於 MCP 通訊。"""
    jsonrpc: str = Field("2.0", pattern="^2.0$")
    id: Union[int, str] = Field(..., description="請求唯一 ID")
    method: str = Field(..., description="MCP 方法名稱")
    params: Optional[dict[str, Any]] = None


class JsonRpcResponse(BaseModel):
    """符合 JSON-RPC 2.0 規範的回應。"""
    jsonrpc: str = "2.0"
    id: Optional[Union[int, str]] = None
    result: Optional[Any] = None
    error: Optional[dict[str, Any]] = None

    @field_validator('error')
    @classmethod
    def validate_error_format(cls, v):
        if v is not None:
            if 'code' not in v or 'message' not in v:
                raise ValueError("JSON-RPC error object must contain 'code' and 'message'")
        return v
