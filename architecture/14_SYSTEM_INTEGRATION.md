SYSTEM INTEGRATION DOCUMENT
Project: Saarthi AI - AI Mentor
Document Version: 1.0
Author: Shreynsh Singh (Solo Developer)
Hackathon: Meta Global AI Developer Hackathon
Deadline: 2nd November 2026
Related Docs: ARCHITECTURE.md, 07_API_INTEGRATION.md, 08_AGENT_CONNECTION.md, 13_MCP_CONNECTION.md

1. Purpose
   This document defines how all different parts of Saarthi AI are integrated into one working system.

Components to integrate:

Next.js Frontend
Java Spring Boot Backend (Orchestrator)
Python AI Service (Sign Language)
Python MCP Server (Memory/Context)
PostgreSQL + Redis
Groq API (Llama 3.3 + Whisper)
Edge-TTS + D-ID Avatar API
MediaPipe (Emotion + Hands in Browser)
Integration Principle: Integrate ONE vertical flow at a time, test it, lock it, then add the next component. Never integrate all components on the same day.

2. Integration Architecture
   mermaid

graph TB
FE[Next.js Frontend<br/>MediaPipe + Avatar Player] <-->|REST + WSS + JWT| JAVA[Java Spring Boot<br/>ORCHESTRATOR]
JAVA -->|JPA| PG[(PostgreSQL)]
JAVA -->|Cache / Session| RD[(Redis)]
JAVA -->|HTTPS Streaming| GROQ[Groq<br/>Llama + Whisper]
JAVA -->|HTTPS| TTS[Edge-TTS]
JAVA -->|HTTPS + WebRTC| DID[D-ID Avatar]
JAVA -->|REST - Landmarks| PY[Python FastAPI<br/>Sign Agent]
JAVA -->|MCP JSON-RPC| MCP[Python MCP Server<br/>Memory Tools]
MCP -->|Read-Only SQL| PG
DID -->|WebRTC Video| FE
Golden Rule of Integration: Frontend integrates ONLY with Java. Python, MCP, Groq, and D-ID integrate ONLY with Java. Java is the single integration hub.

3. Integration Points Matrix
#	From	To	Protocol	Data Format	Auth	Doc Reference
IP-01	Frontend	Java REST	HTTPS	JSON	Bearer JWT	07_API_INTEGRATION.md
IP-02	Frontend	Java WebSocket	WSS	JSON + Audio Base64	JWT in ?token=	07_API_INTEGRATION.md
IP-03	Java	PostgreSQL	JDBC	SQL via JPA	DB User/Pass	06_DATABASE_CONNECTION.md
IP-04	Java	Redis	TCP	Key-Value	Redis Password (Prod)	06_DATABASE_CONNECTION.md
IP-05	Java	Groq Llama	HTTPS	OpenAI JSON, SSE Stream	GROQ_API_KEY	07_API_INTEGRATION.md
IP-06	Java	Groq Whisper	HTTPS	Multipart Audio	GROQ_API_KEY	11_FEATURES_IMPLEMENTATION.md F-03
IP-07	Java	Edge-TTS	Library/CLI	Text -> MP3	No Key	F-04
IP-08	Java	D-ID	HTTPS + WebRTC	JSON + SDP	DID_API_KEY	F-08, F-09
IP-09	Java	Python Sign Service	HTTP REST	Landmarks JSON	Internal Only	08_AGENT_CONNECTION.md
IP-10	Java	MCP Server	Streamable HTTP	JSON-RPC 2.0	Internal Key (Prod)	13_MCP_CONNECTION.md
IP-11	Browser	MediaPipe	WASM (Local)	Video -> Label	No Network	F-06, F-10
IP-12	D-ID	Frontend	WebRTC	Video Stream	D-ID Session ID	F-08
4. Shared Contracts - Must Match Everywhere
   Integration fails when contracts mismatch. Freeze these before coding.

4.1 Common DTO: AgentContext
Used between Frontend -> Java -> All Agents. Defined in 08_AGENT_CONNECTION.md.

java

public record AgentContext(
UUID conversationId, UUID userId,
String textInput, String signInput,
String emotion, Double emotionConfidence,
String inputMode // TEXT / VOICE / SIGN
) {}
4.2 WebSocket Message Types
Frontend and Java must use IDENTICAL strings. No typos.

Direction	Types
Client -> Server	USER_MESSAGE
Server -> Client	TRANSCRIPTION, MENTOR_RESPONSE_CHUNK, MENTOR_RESPONSE_END, CRISIS_ALERT, ERROR
4.3 Standard REST Response
All Java REST APIs return: { "success": true/false, "message": "...", "data": {...} } Frontend must parse only this format.

4.4 Enum Values
Must be identical in PostgreSQL, Java, and TypeScript: HAPPY, SAD, ANXIOUS, STRESSED, TIRED, NEUTRAL, ANGRY and TEXT, VOICE, SIGN

5. Environment Alignment
   A #1 cause of integration failure is mismatched URLs/keys. Maintain this single table in all .env files.

Variable	Frontend .env.local	Java .env	Python .env	MCP .env
API URL	NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1	-	-	-
WS URL	NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws/mentor	-	-	-
Frontend URL	-	FRONTEND_URL=http://localhost:3000	-	-
DB Host/Name/User/Pass	-	✅ Required	-	✅ Read-Only User
Redis Host	-	REDIS_HOST=localhost	-	-
Groq Key	❌ NEVER	GROQ_API_KEY=	-	-
D-ID Key	❌ NEVER	DID_API_KEY=	-	-
JWT Secret	❌ NEVER	JWT_SECRET=	-	-
Python URL	-	PYTHON_SERVICE_URL=http://localhost:8000	-	-
MCP URL	-	MCP_SERVER_URL=http://localhost:8001	-	-
Rule: If a key appears in Frontend env, integration is insecure. Fix immediately.

6. Step-by-Step Integration Plan (Do In This Exact Order)
   Integration Step 1: Database <-> Java
   Integrate: IP-03, IP-04
   Tasks: Run docker-compose up -d, configure application.yml, run Flyway migration from 06_DATABASE_CONNECTION.md.
   Test: GET /actuator/health returns db: UP, redis: UP. Save a test user via repository.
   Do Not Proceed Until: This passes. Everything else depends on DB.

Step 2: Java <-> Groq Llama (No Frontend)
Integrate: IP-05
Tasks: Implement MentorBrainAgent, load System Prompt from 03_MENTOR_SYSTEM_PROMPT.md, implement SSE streaming parser.
Test: Postman/cURL to a temporary test endpoint or unit test. Send "I am stressed" -> streamed Hinglish reply, first token < 800ms.

Step 3: Frontend <-> Java REST (Auth)
Integrate: IP-01
Tasks: Implement SecurityConfig + JwtAuthFilter from 12_SECURITY_AND_AUTHENTICATION.md, connect Login/Register pages, store JWT, handle global 401 -> /login. Configure CORS for localhost:3000 only.
Test: Login from browser, call GET /mood/history with token -> 200. Without token -> 401.

Step 4: Frontend <-> Java WebSocket (Text Only)
Integrate: IP-02
Tasks: Implement MentorWebSocketHandler + MentorOrchestrator, connect Next.js WebSocket client with ?token=.
Test: Type message in browser -> streamed reply appears token-by-token. Wrong token -> connection closed 4401.

Step 5: Java <-> Whisper + TTS (Voice Loop)
Integrate: IP-06, IP-07
Tasks: Frontend VoiceRecorder (MediaRecorder API) -> SpeechAgent -> Llama -> sentence buffering -> VoiceAgent -> audio playback + subtitles.
Test: Speak in Hindi, English, Hinglish -> correct transcription -> spoken reply in same language. Total loop < 3s.

Step 6: Browser MediaPipe <-> Frontend <-> Java (Emotion)
Integrate: IP-11 + IP-02
Tasks: Build EmotionDetector.tsx, attach emotion + confidence to every WebSocket message.
Test: Network tab shows ONLY {emotion: "STRESSED"} sent, no video. Say "I'm fine" while looking sad -> emotion-aware reply (F-07).

Step 7: Java <-> D-ID <-> Frontend (Avatar)
Integrate: IP-08, IP-12
Tasks: Implement AvatarAgent interface + DidAvatarServiceImpl, POST /mentor/upload-avatar, render WebRTC stream in AvatarPlayer.tsx. Implement static-image fallback.
Test: Upload photo -> avatar created < 60s -> same avatar lip-syncs in live session. Disable D-ID in config -> fallback works, session does not crash.

Step 8: Frontend + Java <-> Python Sign Service
Integrate: IP-09 + IP-11
Tasks: SignDetector.tsx sends 21 landmarks, Java SignAgentClient (2s timeout) calls POST /predict-sign, Python returns sign.
Test: Show HELP sign -> mentor responds. Kill Python service -> voice/text chat still works. This failure test is mandatory.

Step 9: Java <-> MCP Server (Memory Context)
Integrate: IP-10
Tasks: Start MCP Server from 13_MCP_CONNECTION.md, add Spring AI MCP Client, fetch profile + mood context ONCE at session start, cache in Redis (TTL 10 min). Keep MCP_ENABLED=false fallback to direct JPA.
Test: Seed STRESSED moods, start session -> first reply references stressful week. Kill MCP Server -> session still works.

Step 10: Mood Dashboard + Crisis Safety (Closing the Loop)
Integrate: All previous IPs
Tasks: MemoryAgent saves message + mood_log after every reply, GET /mood/history feeds dashboard chart, SafetyAgent gate runs before Llama on every message.
Test: Complete a full chat -> mood appears on dashboard. Send crisis phrase -> CRISIS_ALERT in < 1s, Llama is NOT called.

7. End-to-End Integration Flow
   mermaid

sequenceDiagram
participant FE as Next.js + MediaPipe
participant JAVA as Java Orchestrator
participant MCP as MCP Server
participant AI as Groq / TTS / D-ID
participant DB as PostgreSQL/Redis
​
FE->>JAVA: Login (REST) -> JWT
FE->>JAVA: Upload Avatar (REST+JWT)
JAVA->>AI: Create D-ID Presenter
JAVA->>DB: Save mentor_profile
FE->>JAVA: WSS Connect ?token=JWT
JAVA->>MCP: get_user_profile + get_recent_moods
MCP-->>JAVA: Context
FE->>JAVA: USER_MESSAGE {audio, emotion: STRESSED}
JAVA->>AI: Whisper -> Text
JAVA->>JAVA: SafetyAgent Check
JAVA->>AI: Llama (Prompt+Emotion+MCP Context) -> Stream
JAVA->>AI: Sentence -> TTS -> D-ID
AI-->>FE: Avatar WebRTC + Audio + Subtitles
JAVA->>DB: Save Message + MoodLog
8. Integration Error Handling Rules
   Never let one component's failure crash the whole integration.

Failure At	System Behavior
Emotion (Camera Off)	Continue with emotion = NEUTRAL
Python Sign Service Down	Ignore sign input, voice/text continue
MCP Server Down	Use empty context, log warning
D-ID Down / No Credits	Static photo + audio only
Whisper Fails	Send ERROR: "I couldn't hear that clearly. Take your time, I'm here."
Llama Timeout > 10s	Speak fallback line, do not leave user hanging
DB Slow	Serve mood from Redis cache, save async if possible
Implement timeouts per integration point: MCP 5s, Python 2s, Groq 10s. Never use infinite waits.

9. CORS, Security & Network Integration Checklist
   CORS allows ONLY frontend origin, never * with credentials
   All REST + WS calls carry JWT; Java extracts userId from JWT only, never from request body
   Groq/D-ID keys exist only in Java .env, visible nowhere in browser Network/Console
   Python and MCP services are not called directly from Frontend (check browser Network tab)
   Production uses https:// and wss:// everywhere, no mixed content
10. Integration Testing Plan
    10.1 Component Pair Tests (After Each Step)
    Test each IP row from Section 3 in isolation with Postman, wscat, MCP Inspector, and DBeaver before combining.

10.2 Full Golden Flow Test (Final Acceptance)
Run this exact script on the integrated system:

Register new user -> Login -> JWT received
Upload mentor photo -> Avatar READY
Start live session -> MCP context loaded (check Java logs)
Speak Hinglish sentence -> Transcription correct -> Avatar replies with lip-sync < 3s
Change facial expression -> Emotion badge updates -> Reply adapts
Show HELP sign -> Mentor responds to sign
End session -> Open dashboard -> Today's mood visible
Start new session, send crisis phrase -> Helpline modal appears
Logout -> Reuse old token -> 401
If all 9 pass locally AND on deployed URLs, integration is complete.

10.3 Cross-Browser / Device Test
Test Golden Flow on Chrome (Desktop) and Chrome (Android) - camera/mic permissions and WebRTC behave differently on mobile.

11. Common Integration Failures & Fixes
    Symptom	Root Cause	Fix
    CORS error in browser console	Frontend origin not in Java CORS config	Add exact Vercel/localhost URL to SecurityConfig
    WebSocket connects then closes immediately	JWT missing/expired in query param	Check token encoding, validate in handshake interceptor
    Frontend gets full reply at once, not streamed	Java buffers Llama response	Use Flux<String> + SSE parser, flush per chunk
    Audio plays but avatar mouth doesn't move	TTS audio not sent to D-ID stream	Check AvatarAgent receives same audio bytes played in frontend
    MCP context never appears in reply	MCP called but not injected into prompt	Log final Llama prompt; inject mood context before user text
    Python sign works locally, fails after deploy	Java uses localhost:8000 in prod	Set PYTHON_SERVICE_URL to deployed Render URL
    Works on localhost, fails on Vercel/Render	Mixed http/ws vs https/wss	Update all prod env URLs to secure protocols
12. Production Integration (Deployment Link-Up)
    Integrate in this order when deploying (details in upcoming DEPLOYMENT_GUIDE.md):

Supabase PostgreSQL -> get JDBC URL
Upstash Redis -> get host/password
Deploy MCP Server (Render Private) -> set read-only DB env
Deploy Python Sign Service (Render) -> note its URL
Deploy Java Backend (Render) -> set ALL env vars pointing to 1-4 + Groq + D-ID
Deploy Frontend (Vercel) -> set NEXT_PUBLIC_API_URL + NEXT_PUBLIC_WS_URL to Java's Render URL (https/wss)
Update Java FRONTEND_URL CORS to Vercel domain, redeploy Java
Re-run Full Golden Flow Test (Section 10.2) on live URLs
13. Logging For Integration Debugging
    Add a correlation ID per session and log every integration hop in Java:

[Session: uuid] [IP-02] WS message received, emotion=STRESSED
[Session: uuid] [IP-06] Whisper latency: 380ms
[Session: uuid] [IP-10] MCP get_recent_moods latency: 110ms
[Session: uuid] [IP-05] Llama first token: 520ms
[Session: uuid] [IP-08] D-ID stream started

This single practice will save you hours during integration and demo-day debugging. Never log passwords, JWTs, API keys, or full audio/video data.

14. Definition of Done
    System Integration is complete when:

All 12 integration points (IP-01 to IP-12) pass individual pair tests
Full Golden Flow (Section 10.2) passes end-to-end locally
Killing Python or MCP service does not break core voice/avatar flow
No AI key or video frame is visible in browser
Total speech-to-avatar response latency is < 3 seconds
Same Golden Flow passes on deployed Vercel + Render URLs
