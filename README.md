# Stream Guardian 🛡️

> AI驅動的直播聊天室智能審核系統

Stream Guardian 是一個基於 AI 的即時聊天審核系統，採用 LangGraph + MCP (Model Context Protocol) 架構，提供自動化的內容審核、違規檢測和管理動作執行。

## ✨ 核心特性

- **🤖 AI 智能審核**：使用 LangGraph Pipeline 進行多階段內容分析
- **🔧 MCP 工具整合**：支援 Model Context Protocol 2025 標準
- **⚡ 即時處理**：WebSocket 連接實現毫秒級響應
- **🏢 多租戶支援**：支援多個直播頻道同時運行
- **🎯 精準決策**：基於違規類型和信心分數的智能路由
- **🔄 可擴展架構**：微服務設計，易於水平擴展

## 🏗️ 系統架構

```
WebSocket Client
       │
       ↓ ws://localhost:8080/ws/chat

┌──────────────────────────────────────────┐
│         Java Service (:8080)             │
│  - WebSocket Handler                     │
│  - Message Forwarding                    │
│  - MCP Server (Tools: ban/timeout/reply) │
└───────┬──────────────────────────┬───────┘
        │                          │
        │ Kafka                    │ MCP SSE
        │                          │ (Tool Calls)
        ↓                          ↓
┌──────────────────────────────────────────┐
│        Python Service (:8000)            │
│  - FastAPI Endpoint                      │
│  - LangGraph Pipeline                    │
│    • Screener → Moderator → Tools        │
│  - MCP Client (Session)                  │
└───────────────┬──────────────────────────┘
                │
                ↓ Vector Search (In Progress)
        ┌───────────────┐
        │ Qdrant (:6333)│
        └───────────────┘
```

## 🛠️ 技術棧

### Backend Services

| 組件 | 技術 | 版本 | 用途 |
|------|------|------|------|
| **Java Service** | Spring Boot | 3.2.5 | WebSocket 接入 + MCP Server |
| **Python Service** | FastAPI | 0.128+ | AI 分析 + MCP Client |
| **Vector DB** | Qdrant | Latest | 語意搜索和記憶 |

### AI & LLM

| 組件 | 用途 |
|------|------|
| **LangGraph** | AI 工作流編排 |
| **LangChain** | LLM 框架和工具鏈 |
| **MCP SDK** | Model Context Protocol |
| **支援模型** | Claude (Anthropic), GPT (OpenAI), Gemini (Google) |

### 前端

| 組件 | 技術 |
|------|------|
| **聊天模擬器** | Vanilla JavaScript + WebSocket |
| **樣式** | CSS3 (漸層背景 + 玻璃形態) |

## 🚀 快速開始

### 前置需求

- Docker 20.10+
- Docker Compose 2.0+
- 至少一個 AI API Key (Anthropic/OpenAI/Google)

### 環境變數設定

創建 `.env` 檔案於專案根目錄：

```bash
# AI Model API Keys (至少需要一個)
ANTHROPIC_API_KEY=sk-ant-xxxxx
OPENAI_API_KEY=sk-xxxxx
GOOGLE_API_KEY=xxxxx

# LLM 模型選擇 (優先順序: Google > Anthropic > OpenAI)
LLM_MODEL=claude-3-5-sonnet-20241022

# LangSmith 追蹤 (選填)
LANGCHAIN_API_KEY=lsv2_xxxxx
LANGCHAIN_TRACING_V2=true
LANGCHAIN_PROJECT=stream-guardian

# 服務配置 (使用預設值即可)
PYTHON_SERVICE_URL=http://python-service:8000
JAVA_SERVICE_URL=http://java-service:8080
QDRANT_HOST=qdrant
QDRANT_PORT=6333
```

### 啟動服務

```bash
# 1. 啟動所有服務
docker compose up -d --build

# 2. 檢查服務狀態
docker compose ps

# 3. 查看服務日誌
docker compose logs -f python-service
docker compose logs -f java-service
```

### 健康檢查

```bash
# Java Service
curl http://localhost:8080/mcp/health

# Python Service
curl http://localhost:8000/health

# Qdrant
curl http://localhost:6333/health
```

### 開啟前端模擬器

在瀏覽器中開啟：`file:///d:/Stream-Guardian v2/frontend/index.html`

或使用簡易 HTTP 伺服器：

```bash
cd frontend
python -m http.server 3000
# 訪問 http://localhost:3000
```

## 📡 API 文件

### WebSocket API

#### 連接端點

```
ws://localhost:8080/ws/chat
```

#### 發送訊息格式

```json
{
  "tenant_id": "channel_123",
  "user_id": "user_456",
  "username": "john_doe",
  "text": "Hello chat!",
  "timestamp": 1704067200000
}
```

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| `tenant_id` | string | ✅ | 頻道/租戶識別碼 |
| `user_id` | string | ✅ | 使用者識別碼 |
| `username` | string | ❌ | 使用者顯示名稱 |
| `text` | string | ✅ | 訊息內容 |
| `timestamp` | number | ❌ | Unix timestamp (ms)，自動補充 |

#### 接收訊息格式

**審核結果 (moderation_result):**

```json
{
  "type": "moderation_result",
  "message_id": "msg_1704067200000",
  "is_flagged": true,
  "flag_reasons": ["hate_speech", "harassment"],
  "confidence": 0.95,
  "action_taken": "ban_user",
  "action_result": {
    "success": true,
    "action": "ban_user",
    "message": "User user_456 has been banned"
  }
}
```

**機器人訊息 (bot_message):**

```json
{
  "type": "bot_message",
  "message": "系統訊息：ID user_456已被封鎖。"
}
```

### REST API

#### POST /api/analyze

分析聊天訊息並執行審核動作。

**請求範例：**

```bash
curl -X POST http://localhost:8000/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "tenant_id": "channel_123",
    "user_id": "user_456",
    "username": "john_doe",
    "text": "Hello chat!",
    "timestamp": 1704067200000
  }'
```

**回應範例：**

```json
{
  "tenant_id": "channel_123",
  "user_id": "user_456",
  "original_text": "Hello chat!",
  "is_flagged": false,
  "flag_reasons": [],
  "confidence": 0.98,
  "intent": null,
  "action_taken": null,
  "action_result": null,
  "model_name": "claude-3-5-sonnet-20241022"
}
```

**違規訊息範例：**

```json
{
  "tenant_id": "channel_123",
  "user_id": "user_789",
  "original_text": "去死吧！",
  "is_flagged": true,
  "flag_reasons": ["hate_speech", "harassment"],
  "confidence": 0.95,
  "intent": "ban",
  "action_taken": "ban_user",
  "action_result": {
    "success": true,
    "action": "ban_user",
    "tenant_id": "channel_123",
    "user_id": "user_789",
    "reason": "hate_speech, harassment"
  },
  "model_name": "claude-3-5-sonnet-20241022"
}
```

## 🔧 MCP 工具

### ban_user

永久封禁使用者。

**參數：**
- `tenant_id` (string): 頻道識別碼
- `user_id` (string): 使用者識別碼
- `reason` (string): 封禁原因

**適用場景：** 嚴重違規（仇恨言論、騷擾、垃圾機器人）

### timeout_user

暫時禁言使用者。

**參數：**
- `tenant_id` (string): 頻道識別碼
- `user_id` (string): 使用者識別碼
- `duration` (integer): 禁言時長（秒）

**適用場景：** 輕度違規（垃圾訊息、不當內容）

### reply_chat

以機器人身份發送訊息。

**參數：**
- `tenant_id` (string): 頻道識別碼
- `message` (string): 訊息內容

**適用場景：** 提供資訊、社群互動

## 🧠 LangGraph 審核流程

### Pipeline 架構

```
START → Screener Node → Decision → Moderator Node → ToolNode → Decision Node → END
           │                │           │              │            │
           │                │           │              │            └─→ 強制結束
           │                │           │              └─→ MCP Tools (ban/timeout/reply)
           │                │           └─→ AI 決策 (選擇工具)
           │                └─→ is_flagged=false → END
           └─→ 內容分析 (is_flagged, flag_reasons, confidence)
```

**流程說明：**

1. **Screener Node** - 初始分類
   - 分析訊息內容
   - 輸出：`is_flagged`, `flag_reasons`, `confidence`

2. **Decision** - 路由決策
   - 若 `is_flagged=false` → 直接結束
   - 若 `is_flagged=true` 且 `confidence > 0.5` → 進入 Moderator

3. **Moderator Node** - 決策執行
   - 根據違規類型選擇工具
   - 使用 LLM 決定採取的行動

4. **ToolNode** - 工具執行
   - 透過 MCP 呼叫 Java Service 的工具
   - 執行實際的管理動作

5. **Decision Node** - 強制結束
   - 確保只執行一次工具
   - 防止循環執行

### 違規類型定義

| 類型 | 說明 | 範例 | 處理方式 |
|------|------|------|---------|
| **hate_speech** | 仇恨言論、歧視、威脅 | "XXX去死" | ban_user |
| **harassment** | 騷擾、人身攻擊、霸凌 | "你是白痴嗎？" | ban_user |
| **spam** | 廣告、推銷、釣魚連結 | "免費領取 bit.ly/xxx" | timeout_user |
| **inappropriate** | 色情、暴力、不當內容 | 18+ 內容 | timeout_user |

### 信心分數閾值

- **confidence > 0.5**: 進入審核流程
- **confidence ≤ 0.5**: 直接放行

## 📁 專案結構

```
Stream-Guardian-v2/
├── java-service/                    # Java 微服務
│   ├── src/main/java/com/streamguardian/
│   │   ├── ingest/
│   │   │   ├── handler/
│   │   │   │   └── ChatWebSocketHandler.java
│   │   │   └── service/
│   │   │       └── MessageForwardingService.java
│   │   └── mcp/
│   │       ├── controller/
│   │       │   └── McpController.java
│   │       └── service/
│   │           └── McpToolService.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── python-service/                  # Python 微服務
│   ├── app/
│   │   ├── graph/
│   │   │   ├── builder.py          # LangGraph 構建器
│   │   │   ├── nodes.py            # Pipeline 節點
│   │   │   └── state.py            # State 定義
│   │   ├── main.py                 # FastAPI 應用
│   │   ├── models.py               # Pydantic 模型
│   │   └── config.py               # 配置管理
│   └── requirements.txt
│
├── frontend/                        # 前端模擬器
│   └── index.html
│
├── docker-compose.yml               # Docker 編排
├── .env                            # 環境變數
└── README.md                       # 本文件
```

## 🔍 監控與除錯

### 查看服務日誌

```bash
# 所有服務
docker compose logs -f

# 特定服務
docker compose logs -f python-service
docker compose logs -f java-service
docker compose logs -f qdrant
```

### LangSmith 追蹤

若啟用 LangSmith 追蹤，可在 [smith.langchain.com](https://smith.langchain.com) 查看：

1. 前往 Projects → `stream-guardian`
2. 查看每個訊息的完整 trace：
   - Screener node LLM 呼叫
   - Moderator node 決策
   - MCP Tool 執行

### 常見日誌標記

| 標記 | 說明 |
|------|------|
| `========== starting_up` | 服務啟動 |
| `========== mcp_session_started` | MCP 連接建立 |
| `========== graph_initialized` | LangGraph 初始化完成 |
| `========== api/analyze called` | 收到分析請求 |
| `========== screener_result` | Screener 分析結果 |
| `========== moderator_response` | Moderator 決策結果 |
| `========== MCP TOOL EXECUTION` | 工具執行 |

## 🚧 開發指南

### 本地開發環境

**Java Service:**

```bash
cd java-service
./mvnw spring-boot:run
```

**Python Service:**

```bash
cd python-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### 添加新的 MCP 工具

1. 在 `McpToolService.java` 添加工具定義：

```java
private McpToolDefinition buildNewTool() {
    Map<String, PropertyDefinition> properties = new LinkedHashMap<>();
    properties.put("param1", PropertyDefinition.builder()
        .type("string")
        .description("Parameter description")
        .build());

    return McpToolDefinition.builder()
        .name("new_tool")
        .description("Tool description")
        .inputSchema(InputSchema.builder()
            .type("object")
            .properties(properties)
            .required(List.of("param1"))
            .build())
        .build();
}
```

2. 實作工具執行邏輯：

```java
private Map<String, Object> executeNewTool(Map<String, Object> args) {
    String param1 = (String) args.get("param1");

    // 執行邏輯

    return Map.of(
        "success", true,
        "action", "new_tool",
        "result", "..."
    );
}
```

3. 在 `executeTool()` switch 中添加 case

### 修改審核規則

編輯 `python-service/app/graph/nodes.py`:

```python
SCREENER_SYSTEM_PROMPT = """你是直播聊天室審核 AI。分析訊息是否違反以下規則：
- new_rule: 新規則描述
- spam: 廣告、推銷、釣魚連結
...
"""

MODERATOR_SYSTEM_PROMPT = """決策原則：
- 新違規類型: 使用 new_tool
- 嚴重違規: 使用 ban_user
...
"""
```

## 🧪 測試

### 使用前端模擬器測試

1. 開啟 `frontend/index.html`
2. 點擊「連線」
3. 使用快速測試按鈕發送預設訊息

### 使用 curl 測試

```bash
# 發送安全訊息
curl -X POST http://localhost:8000/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "tenant_id": "test-channel",
    "user_id": "user_001",
    "username": "TestUser",
    "text": "大家好！",
    "timestamp": 1704067200000
  }'

# 發送違規訊息
curl -X POST http://localhost:8000/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "tenant_id": "test-channel",
    "user_id": "user_002",
    "username": "BadUser",
    "text": "去死吧！",
    "timestamp": 1704067200000
  }'
```

### 使用 WebSocket 測試工具

```bash
# 安裝 wscat
npm install -g wscat

# 連接
wscat -c ws://localhost:8080/ws/chat

# 發送訊息
{"tenant_id":"test","user_id":"u1","username":"user1","text":"Hello"}
```

## 🔒 安全考量

- ✅ WebSocket 連接支援 CORS (可配置)
- ✅ API Key 透過環境變數注入
- ✅ 敏感資料不寫入日誌
- ⚠️ **生產環境建議**：
  - 添加 JWT 驗證
  - 啟用 SSL/TLS
  - 使用 API Gateway
  - 實作 Rate Limiting

## 📈 擴展性

### 水平擴展

```yaml
# docker-compose.yml
services:
  java-service:
    deploy:
      replicas: 3
  python-service:
    deploy:
      replicas: 2
```

### 負載平衡

建議在前端添加 Nginx/Traefik:

```nginx
upstream java_backend {
    server java-service-1:8080;
    server java-service-2:8080;
    server java-service-3:8080;
}
```

## 🆘 疑難排解

### Python Service 無法連接 Java Service

**錯誤訊息：** `Connection refused to java-service:8080`

**解決方案：**
1. 確認 Java Service 已啟動：`docker compose ps`
2. 檢查健康檢查：`curl http://localhost:8080/mcp/health`
3. 查看日誌：`docker compose logs java-service`

### MCP Session Timeout

**錯誤訊息：** `SSE connection timeout`

**解決方案：**
- 已改用 HTTP transport 避免此問題
- 確認 `main.py` 中 transport 設定為 `"http"`

### LLM API Key 無效

**錯誤訊息：** `Authentication failed`

**解決方案：**
1. 檢查 `.env` 檔案中的 API Key
2. 確認至少有一個有效的 API Key
3. 重啟服務：`docker compose restart python-service`

### Tool 執行失敗

**錯誤訊息：** `Tool execution error`

**解決方案：**
1. 檢查 Java Service 日誌：`docker compose logs java-service | grep MCP`
2. 確認 MCP 連接狀態
3. 驗證工具參數格式

## 📧 聯絡方式

如有問題或建議，歡迎提交 Issue 或 Pull Request。

---

**Built with ❤️ using LangGraph, FastAPI, and Spring Boot**
