MCP (MODEL CONTEXT PROTOCOL) CONNECTION DOCUMENT
Project: Saarthi AI - AI Mentor Document Version: 1.0 Author: Shreynsh Singh (Solo Developer) Hackathon: Meta Global AI Developer Hackathon Deadline: 2nd November 2026 Orchestrator: Java 21 + Spring Boot 3.2 Related Docs: ARCHITECTURE.md, 07_API_INTEGRATION.md, 08_AGENT_CONNECTION.md, 12_SECURITY_AND_AUTHENTICATION.md

1. Purpose
   This document defines how to connect Saarthi AI to external tools, memory, and data sources using MCP - Model Context Protocol.

MCP is an open standard (created by Anthropic, now adopted broadly) that defines ONE universal way for an AI model to discover and call tools, read resources, and use prompts — instead of writing custom integration code for every data source.

After implementing this document:

Llama will not just chat, it will fetch real context before replying
Mood history, user profile, and safety resources will be exposed as standard MCP Tools
Any future tool (Calendar, Journal, Study Planner) can be plugged in without changing Llama code
2. What Is MCP? (In Simple Words)
   Think of MCP like USB for AI.

Before USB, every device needed a different cable. MCP does the same for AI:

Without MCP: Llama -> Custom Java Code -> PostgreSQL
Llama -> Custom Java Code -> Redis
Llama -> Custom Java Code -> Crisis Helplines List
​
With MCP:    Llama -> MCP Client (Java) -> MCP Server -> PostgreSQL
-> Redis
-> Any Tool

3 core MCP concepts you will use:

Concept	Meaning in Saarthi
Tools	Functions Llama can call: get_recent_moods(userId), save_mood_log(...), get_helpline_resources(country)
Resources	Read-only data Llama can read: saarthi://user/{id}/profile, saarthi://user/{id}/mood-summary
Prompts	Reusable templates: Saarthi Mentor System Prompt served via MCP
Transport: JSON-RPC 2.0 over Streamable HTTP / SSE. For local dev, stdio is also supported.

3. Why Saarthi Needs MCP (And Where It Does NOT)
   3.1 Use MCP For ✅
   Memory Agent: Fetch last 7 days mood, last conversation summary before starting a new session
   Personalization: Fetch user name, language, mentor profile, goals
   Safety Agent: Fetch verified helpline resources dynamically, not hardcoded
   Future Tools: Journal entries, study timetable, breathing exercises library
   3.2 Do NOT Use MCP For ❌
   Real-time audio chunks, Whisper, TTS, Avatar lip-sync — keep your direct WebSocket + Groq/D-ID flow from 08_AGENT_CONNECTION.md. MCP would add latency.
   Emotion / Sign landmark streaming — too high-frequency for MCP.
   Architecture Rule: MCP is for context before & after conversation. WebSocket is for live conversation.

4. MCP Architecture for Saarthi
   mermaid

graph TD
FE[Next.js Frontend] <-->|WSS Live Chat| JAVA[Java Spring Boot<br/>Mentor Orchestrator]
​
subgraph MCP_Client["MCP Client - Inside Java"]
MC[MCP Client<br/>Spring AI MCP Client]
end
​
subgraph MCP_Servers["MCP Servers - Python / Java"]
S1[Mood Memory MCP Server<br/>Python FastMCP]
S2[User Profile MCP Server<br/>Python FastMCP]
S3[Safety Resources MCP Server<br/>Python FastMCP]
end
​
JAVA --> MC
MC -->|JSON-RPC / HTTP + SSE| S1
MC -->|JSON-RPC / HTTP + SSE| S2
MC -->|JSON-RPC / HTTP + SSE| S3
​
S1 --> PG[(PostgreSQL<br/>mood_logs, messages)]
S2 --> PG
S3 --> JSON[helplines.json / DB]
​
JAVA -->|Streaming Chat| LLAMA[Llama 3.3 70B via Groq]
MC -.->|Context Injected into Prompt| LLAMA
Key Design Decision
Your MemoryAgent from 08_AGENT_CONNECTION.md will be refactored into an MCP Client. It will no longer query the database with hardcoded JPA logic for Llama context — it will discover and call MCP Tools. Direct JPA saves (saving a new message) stay in Java for speed and security.

5. MCP Servers To Implement
   Implement only ONE combined Python MCP Server for MVP to save solo time. Split later in V2.

Server Name: saarthi-mcp-server (Python)

5.1 Tools Exposed
Tool Name	Input	Output	Used When
get_recent_moods	user_id: string, days: int = 7	[{mood, confidence, logged_at}]	Before session starts
get_conversation_summary	user_id: string, limit: int = 3	Last 3 session summaries	Before session starts
get_user_profile	user_id: string	{name, preferred_language, mentor_name}	Personalize greeting
get_helpline_resources	country: string = "IN"	[{name, phone}] e.g. Tele-MANAS 14416	Crisis flow
5.2 Resources Exposed
Resource URI	Description
saarthi://user/{user_id}/mood-summary	Human-readable 7-day mood summary for Llama
saarthi://safety/helplines/IN	Verified India helpline list
5.3 Security Rule for Every Tool
Every tool MUST receive user_id derived from the validated JWT in Java (see 12_SECURITY_AND_AUTHENTICATION.md), never from the frontend or from Llama's own generation. The MCP Client injects it. If user_id is missing, the server rejects the call.

6. Project Structure Addition
   saarthi-ai/
   ├── mcp-server-python/            # NEW
   │   ├── server.py                 # FastMCP Server - all tools
   │   ├── db.py                     # PostgreSQL read-only connection
   │   ├── requirements.txt
   │   └── .env
   ├── backend-java/
   │   └── src/main/java/com/saarthi/ai/
   │       ├── mcp/
   │       │   ├── McpClientConfig.java
   │       │   └── McpMemoryService.java  # Wraps MCP tool calls
   │       └── agent/core/
   │           └── MemoryAgent.java  # Now delegates to McpMemoryService

7. Implementation - Step 1: Python MCP Server
   7.1 Dependencies (requirements.txt)
   mcp[cli]>=1.2.0
   psycopg2-binary
   python-dotenv
   uvicorn

7.2 Server Code (server.py)
python

import os, psycopg2, json
from dotenv import load_dotenv
from mcp.server.fastmcp import FastMCP
​
load_dotenv()
mcp = FastMCP("saarthi-mcp-server")
​
def get_db():
return psycopg2.connect(
host=os.getenv("DB_HOST", "localhost"),
database=os.getenv("DB_NAME", "saarthi_db"),
user=os.getenv("DB_USERNAME", "saarthi_user"),
password=os.getenv("DB_PASSWORD", "saarthi_password")
)
​
@mcp.tool()
def get_recent_moods(user_id: str, days: int = 7) -> str:
"""Get user's mood history for the last N days. Use this to personalize mentoring."""
conn = get_db()
cur = conn.cursor()
cur.execute(
"SELECT mood, confidence, logged_at FROM mood_logs "
"WHERE user_id = %s AND logged_at >= NOW() - (%s || ' days')::interval "
"ORDER BY logged_at DESC", (user_id, days)
)
rows = [{"mood": r[0], "confidence": r[1], "logged_at": str(r[2])} for r in cur.fetchall()]
cur.close(); conn.close()
return json.dumps(rows)
​
@mcp.tool()
def get_user_profile(user_id: str) -> str:
"""Get user's name, language and mentor preference for personalization."""
conn = get_db()
cur = conn.cursor()
cur.execute("SELECT name, preferred_language FROM users WHERE id = %s", (user_id,))
row = cur.fetchone()
cur.close(); conn.close()
if not row: return json.dumps({"error": "user_not_found"})
return json.dumps({"name": row[0], "preferred_language": row[1]})
​
@mcp.tool()
def get_helpline_resources(country: str = "IN") -> str:
"""Get verified crisis helplines. Always use this in crisis situations."""
return json.dumps([
{"name": "Tele-MANAS", "phone": "14416", "available": "24x7"},
{"name": "AASRA", "phone": "9820466726", "available": "24x7"},
{"name": "iCall", "phone": "9152987821", "available": "Mon-Sat"}
])
​
if __name__ == "__main__":
# For local dev with MCP Inspector / stdio. For HTTP, use transport="streamable-http"
mcp.run(transport="streamable-http")
Run: python server.py — Server starts on http://localhost:8001/mcp

Read-Only Rule: MCP Server DB user should have only SELECT permission on users, mood_logs, conversations, messages. It must never DELETE or UPDATE.

8. Implementation - Step 2: Java MCP Client
   8.1 Dependencies (pom.xml)
   Use Spring AI's MCP Client starter (compatible with Spring Boot 3.2+):

xml

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
    <version>1.0.0</version>
</dependency>
8.2 Configuration (application.yml)
yaml

spring:
ai:
mcp:
client:
enabled: true
name: saarthi-backend
version: 1.0.0
request-timeout: 5s
type: ASYNC  # Non-blocking, fits WebFlux
streamable-http:
connections:
saarthi-mcp:
url: ${MCP_SERVER_URL:http://localhost:8001}
endpoint: /mcp
​
saarthi:
mcp:
enabled: ${MCP_ENABLED:true} # Kill-switch for demo fallback
Add to .env:

env

MCP_SERVER_URL=http://localhost:8001
MCP_ENABLED=true
8.3 McpMemoryService (Wrapper Used by Orchestrator)
java

package com.saarthi.ai.mcp;
​
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.common.autoconfigure.*;
import org.springframework.stereotype.Service;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.UUID;
​
@Slf4j
@Service
@RequiredArgsConstructor
public class McpMemoryService {
​
private final List<McpAsyncClient> mcpClients; // Auto-configured by Spring AI
​
// Called BEFORE Llama. Never block the live WebSocket thread for >5s.
public Mono<String> getMoodContext(UUID userId) {
if (mcpClients.isEmpty()) return Mono.just("[]");
var request = new McpSchema.CallToolRequest(
"get_recent_moods",
Map.of("user_id", userId.toString(), "days", 7)
);
return mcpClients.get(0).callTool(request)
.map(result -> result.content().toString())
.timeout(java.time.Duration.ofSeconds(5))
.onErrorResume(e -> {
log.warn("[MCP] get_recent_moods failed, using fallback: {}", e.getMessage());
return Mono.just("[]"); // Fallback - conversation must continue
});
}
}
8.4 Connect to Orchestrator (08_AGENT_CONNECTION.md Update)
In MentorOrchestrator, BEFORE calling Llama:

java

// 1. Fetch context via MCP (with timeout + fallback)
String moodContext = mcpMemoryService.getMoodContext(userId).block(Duration.ofSeconds(5));
​
// 2. Inject into context, then stream Llama as before
return brainAgent.streamResponse(userText, emotion, moodContext);
This is the only integration point. Do not scatter MCP calls across controllers.

9. End-to-End Flow With MCP
   mermaid

sequenceDiagram
participant U as User
participant JAVA as Java Orchestrator
participant MCP as MCP Server (Python)
participant L as Llama 3.3
​
U->>JAVA: Starts Session (JWT validated)
JAVA->>MCP: CallTool get_user_profile(user_id)
MCP-->>JAVA: {name: Shreynsh, language: en-IN}
JAVA->>MCP: CallTool get_recent_moods(user_id, 7)
MCP-->>JAVA: [{STRESSED}, {TIRED}, {NEUTRAL}]
JAVA->>L: System Prompt + Profile + Mood Context + "I'm fine"
L-->>JAVA: "You said you're fine, but this week looked stressful..."
JAVA-->>U: Avatar speaks response
JAVA->>JAVA: Save message + mood via JPA (NOT via MCP)
Latency Note: MCP calls happen ONCE at session start and are cached in Redis for that session. Never call MCP on every single message — that would break your <3s target.

10. Security for MCP Connection
    Risk	Mitigation
    Llama hallucinates another user's ID	Java injects user_id from JWT. Llama never generates it.
    MCP Server exposed publicly	Run MCP Server as internal service only. Not exposed via Vercel/Frontend. In prod, place behind same private network as Java on Render.
    MCP Server compromises DB	Use read-only DB user for MCP. No write/delete grants.
    Tool abuse / infinite calls	5s timeout + Redis cache + max 3 MCP calls per session in Java.
    Secrets leak	MCP Server .env has only read-only DB password, stored in Render env vars, never in GitHub.
    For MVP local dev, no auth between Java and MCP is acceptable (both on localhost). For production, add a shared header: X-MCP-Internal-Key validated by a FastMCP middleware — document as V2 hardening if time is short.

11. Testing MCP Connection
    11.1 Test Server Alone
    Use MCP Inspector:

bash

npx @modelcontextprotocol/inspector python server.py
Tools list shows get_recent_moods, get_user_profile, get_helpline_resources
Calling get_recent_moods with a real user UUID returns JSON from PostgreSQL
11.2 Test Java Client
Backend starts with log: MCP Client connected: saarthi-mcp-server, tools discovered: 3
Start a session -> Log shows [MCP] get_recent_moods latency: 120ms
Turn OFF MCP Server -> Session still works with empty context (fallback test)
Set MCP_ENABLED=false -> App works fully without MCP (your safety net for demo day)
11.3 Test Personalization
Seed 3 STRESSED moods this week, start session saying "I'm fine" -> Mentor gently references stressful week without saying "MCP tool returned..."
12. Deployment
    Service	Where	Notes
    MCP Server	Render - Private Service (Python)	Same region as Java. Env: read-only DB creds. Port 8001.
    Java Backend	Render - Web Service	Env: MCP_SERVER_URL=https://saarthi-mcp.internal...
    Never deploy MCP Server as a public URL linked from Frontend.

13. Risks & Fallback Strategy
    Risk	Fallback
    MCP adds complexity / bugs before deadline	MCP_ENABLED=false kill-switch reverts MemoryAgent to direct JPA query. Keep that old code path until MCP is proven stable.
    Spring AI MCP version conflicts with Spring Boot 3.2	Pin Spring AI to a Boot-3.2-compatible 1.0.0-Mx release used in your pom.xml, test on Day 1 of MCP work. Alternative: use official Java SDK io.modelcontextprotocol.sdk:mcp directly.
    Tool call latency spikes	Cache result in Redis: key mcp:mood:{userId}, TTL 10 mins per session.
    Solo Developer Recommendation: Implement MCP in Phase 4 (after Avatar works), not Phase 1. It is a powerful differentiator slide for judges, but your core demo must not depend on it.

14. Definition of Done
    MCP Connection is complete when:

Python MCP Server runs and lists 3 tools in MCP Inspector
Java discovers tools on startup with no error
Session start fetches profile + mood context via MCP in < 500ms total
Llama's first reply uses that context naturally
Killing the MCP Server does NOT crash live conversation (fallback works)
No frontend code ever calls the MCP Server directly