"""FastAPI entry point for Stream Guardian Python service."""
from contextlib import asynccontextmanager
import structlog
from fastapi import FastAPI, HTTPException
from fastapi.responses import ORJSONResponse

from app.config import get_settings
from app.models import ChatMessage, AnalysisResult
from app.graph import get_moderation_graph, cleanup

logger = structlog.get_logger()
settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan: startup and shutdown."""
    logger.info("========== starting_up", app=settings.app_name, version=settings.app_version)

    # Pre-warm: 建立 MCP 連線與 Graph
    try:
        await get_moderation_graph()
        logger.info("========== graph_initialized")
    except Exception as e:
        logger.warning("========== graph_init_failed", error=str(e))

    yield

    # Shutdown: 清理資源
    logger.info("========== shutting_down")
    await cleanup()


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    default_response_class=ORJSONResponse,
    lifespan=lifespan
)


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": settings.app_name,
        "version": settings.app_version
    }


@app.post("/api/analyze", response_model=AnalysisResult)
async def analyze_message(message: ChatMessage):
    """
    分析聊天訊息並執行審核動作。
    由 Java Ingest Service 透過 HTTP POST 呼叫。
    """
    logger.info(
        "========== api/analyze called",
        tenant_id=message.tenant_id,
        user_id=message.user_id,
        text=message.text[:50] + "..." if len(message.text) > 50 else message.text
    )

    try:
        graph = await get_moderation_graph()

        # 執行 LangGraph Pipeline
        result = await graph.ainvoke({
            "tenant_id": message.tenant_id,
            "user_id": message.user_id,
            "username": message.username,
            "text": message.text,
            "timestamp": message.timestamp,
            "messages": []
        })

        logger.info(
            "========== analyze_complete",
            tenant_id=message.tenant_id,
            is_flagged=result.get("is_flagged", False),
            action_taken=result.get("action_taken")
        )

        return AnalysisResult(
            tenant_id=message.tenant_id,
            user_id=message.user_id,
            original_text=message.text,
            is_flagged=result.get("is_flagged", False),
            flag_reasons=result.get("flag_reasons", []),
            confidence=result.get("confidence", 0.0),
            action_taken=result.get("action_taken"),
            action_result=result.get("action_result"),
            model_name=result.get("model_name")
        )

    except Exception as e:
        logger.error("========== analyze_failed", error=str(e))
        raise HTTPException(status_code=500, detail=str(e))
