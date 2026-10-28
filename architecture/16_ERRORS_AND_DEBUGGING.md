ERRORS AND DEBUGGING DOCUMENT
Project: Saarthi AI - AI Mentor Document Version: 1.0 Author: Shreynsh Singh (Solo Developer) Hackathon: Meta Global AI Developer Hackathon Deadline: 2nd November 2026 Related Docs: 09_PROJECT_IMPLEMENTATION.md, 14_SYSTEM_INTEGRATION.md, 15_QA_TESTING.md

1. Purpose
   This document defines how to identify, log, debug, and resolve errors across all parts of Saarthi AI:

Next.js Frontend
Java Spring Boot Backend
WebSocket Communication
PostgreSQL & Redis
Groq API - Llama & Whisper
Edge-TTS & D-ID Avatar
MediaPipe - Emotion & Sign Detection
Python AI Service
MCP Server
Security, Authentication & CORS
The goal is simple: Find the root cause quickly, fix it safely, and never break the core demo flow.

2. Debugging Principles for Solo Developer
   Rule 1: Debug One Layer at a Time
   Never debug Frontend, Java, Python, and D-ID together.

Test in this order:

Database -> Java Backend -> External AI API -> WebSocket -> Frontend -> Avatar -> Sign/MCP

Rule 2: Check Logs Before Changing Code
90% of errors are already explained in:

Browser Console
Java Backend Logs
Docker Logs
Network Tab
API Response
Rule 3: Reproduce the Error
Before fixing, note:

What did you click/speak/type?
Which user was logged in?
What was the emotion/input mode?
Local or deployed environment?
Exact error message and timestamp?
Rule 4: Protect the Golden Flow
Your Golden Flow is:

Login -> Start Session -> Speak -> Mentor Replies -> Avatar Talks -> Mood Saved

If a fix breaks this flow, revert the fix.

Rule 5: Never Debug by Guessing
Change one thing, test it, then move to the next change.

3. Debugging Workflow
   mermaid

graph TD
A[Error Occurs] --> B[Note Steps to Reproduce]
B --> C[Check Browser Console & Network Tab]
C --> D[Check Java Logs]
D --> E{Error Layer Identified?}
E -->|Yes| F[Apply Specific Fix]
E -->|No| G[Test Component in Isolation]
G --> E
F --> H[Re-run Failing Test Case]
H --> I{Fixed?}
I -->|Yes| J[Run Golden Flow Regression]
I -->|No| G
J --> K[Document Bug & Fix]
4. Logging Strategy
   4.1 Standard Log Format
   Use the same format in Java, Python, MCP, and Frontend logs:

[Timestamp] [Service] [Session/User ID] [Component] [Level] Message

Example:

2026-10-20 18:30:12 [Java] [User: aarav123] [MentorBrainAgent] [INFO] Llama first token latency: 480ms
2026-10-20 18:30:13 [Java] [User: aarav123] [SpeechAgent] [ERROR] Whisper API timeout after 5000ms

4.2 What to Log
Log This	Never Log This
User ID & Conversation ID	Password
API latency	JWT Token
Emotion label & confidence	Groq/D-ID API Key
Agent name & status	Raw video frames
Error code & stack trace in backend	Full personal conversation in production
WebSocket connect/disconnect	User's private photo path publicly
4.3 Java Logging Configuration
In application.yml:

yaml

logging:
level:
root: INFO
com.saarthi.ai: DEBUG
org.springframework.web.socket: INFO
pattern:
console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
Use this in Java code:

java

log.info("[SpeechAgent] Transcription completed in {}ms", latency);
log.warn("[Emotion] No emotion received, using NEUTRAL");
log.error("[LlamaService] Groq API failed", exception);
4.4 Frontend Logging
Use Browser Console during development only:

typescript

console.log("[WebSocket] Connected");
console.log("[EmotionDetector] Emotion:", emotion);
console.error("[API] Request failed:", error);
Remove or disable sensitive debug logs before final submission.

5. Standard Error Response Format
   All Java REST APIs must return errors in this format only:

json

{
"success": false,
"message": "Invalid email or password",
"errorCode": "AUTH_INVALID_CREDENTIALS"
}
Frontend Rule: Never show Java stack traces, SQL errors, or API provider errors to the user. Show calm, human messages.

Example:

Bad: Groq API 429: Rate limit exceeded
Good: I'm here with you. Please give me a moment and try again.
6. Common Errors - Frontend (Next.js)
   Error / Symptom	Root Cause	Debug Steps	Fix
   Blank page / 500 error	TypeScript or component crash	Check Browser Console & terminal	Fix component error shown in stack trace
   NEXT_PUBLIC_API_URL is undefined	.env.local missing/wrong	Print env in console locally	Create .env.local, restart npm run dev
   API call fails with CORS error	Java CORS doesn't allow frontend origin	Check Network Tab & Java logs	Add exact frontend URL in SecurityConfig
   WebSocket closes immediately	JWT missing, expired, or invalid	Check WS URL in Network Tab	Re-login, pass ?token=<JWT> correctly
   Camera permission denied	User blocked camera or browser issue	Check browser site settings	Show Text Mode fallback & instructions
   Mic records but no transcription	Audio format or WebSocket payload issue	Check audio blob size in console	Send correct audioBase64 & inputMode
   Avatar video is black	D-ID stream not started or WebRTC blocked	Check D-ID response & browser console	Check stream ID, use Chrome, enable fallback image
   Emotion badge always NEUTRAL	MediaPipe not loading or face not visible	Check lighting, console, camera preview	Improve light, check EmotionDetector.tsx initialization
   Subtitles don't match audio	TTS chunks played out of order	Check WebSocket message order	Play audio chunks sequentially
   Frontend Debugging Tools
   Chrome DevTools -> Console
   Chrome DevTools -> Network -> WS tab
   React Developer Tools
   npm run build to catch production build errors
7. Common Errors - Java Backend
   Error / Symptom	Root Cause	Debug Steps	Fix
   Port 8080 already in use	Old Java process running	netstat / Task Manager	Kill old process or change port
   Application fails to start	Missing env variable / DB down	Read first Caused by: in logs	Start Docker, check .env
   ClassNotFoundException	Dependency missing	Check pom.xml	Add dependency, run ./mvnw clean install
   Llama returns empty response	SSE parsing error	Log raw Groq chunk	Fix LlamaService chunk parser
   Responses are not streaming	Flux is being blocked/collected	Check LlamaService code	Return Flux<String>, don't use .collectList().block()
   TTS speaks half sentences	Tokens sent directly to TTS	Check VoiceAgent logs	Buffer until . or । before TTS
   Slow response > 5 seconds	Calling agents sequentially / no timeout	Check agent latency logs	Add timeouts, stream sentence-by-sentence
   NullPointerException	Missing user/emotion/context data	Check stack trace line number	Add null validation in DTO/Orchestrator
   Useful Backend Commands
   bash

./mvnw spring-boot:run
./mvnw clean install -DskipTests
curl http://localhost:8080/actuator/health
8. Common Errors - WebSocket
   Error	Root Cause	Fix
   Connection returns 401 / closes with 4401	JWT invalid/expired/blacklisted	Login again, validate token in handshake interceptor
   Connected but no reply	MentorOrchestrator not called or Llama failed	Check Java logs after message is sent
   Reply comes all at once	Frontend or Java buffering stream	Check Flux streaming & frontend chunk handling
   Audio chunks overlap	Multiple TTS audios played simultaneously	Queue audio playback in frontend
   Connection drops after 30-60 sec	Proxy / Render idle timeout	Send ping/pong heartbeat every 25 seconds
   TextMessage too large	Large audioBase64 in one message	Limit recording length, compress audio
   Test WebSocket without frontend:

bash

wscat -c "ws://localhost:8080/ws/mentor?token=YOUR_JWT"
9. Common Errors - Database & Redis
   Error	Root Cause	Fix
   Connection refused: localhost:5432	Docker/PostgreSQL not running	docker-compose up -d, check docker ps
   password authentication failed	Wrong DB password	Match .env with docker-compose.yml
   relation "users" does not exist	Flyway migration didn't run	Check V1__init_schema.sql location, restart with fresh volume only in dev
   duplicate key: email	Registering same email again	Return proper 400 error from Auth service
   JPA validate failed	Entity doesn't match table column	Match @Column(name="...") with SQL exactly
   Redis connection failed	Redis container down	docker exec -it saarthi-redis redis-cli ping should return PONG
   Mood history empty	mood_logs not saved after chat	Debug MemoryAgent.saveMood() & check userId
   Check DB quickly:

bash

docker-compose logs postgres
docker exec -it saarthi-postgres psql -U saarthi_user -d saarthi_db
10. Common Errors - Groq / Llama / Whisper
    Error	Meaning	Fix
    401 Unauthorized	Wrong GROQ_API_KEY	Create new key at console.groq.com, update .env, restart Java
    429 Rate Limit	Too many requests / free limit hit	Wait, reduce test calls, show calm retry message
    404 Model Not Found	Wrong model name	Use llama-3.3-70b-versatile & whisper-large-v3 exactly
    Whisper returns empty text	Silent/corrupt audio	Check mic recording, audio format, duration
    Whisper transcribes Hindi wrong	Noisy audio / unclear speech	Test closer to mic, reduce background noise
    Llama replies in English to Hinglish	System Prompt not loaded or language rule missing	Verify 03_MENTOR_SYSTEM_PROMPT.md is actually sent as system role
    Llama gives long lectures	Prompt / temperature issue	Enforce 2-3 sentences rule in prompt, lower max tokens for voice
    Llama says "As an AI..."	Generic prompt behavior	Strengthen Saarthi personality section in system prompt
    Debug Rule: First test Groq with a simple cURL/Postman call outside Java. If it fails there, it's not your Java code.

11. Common Errors - TTS & D-ID Avatar
    Error / Symptom	Root Cause	Fix
    No voice output	Edge-TTS failed or audio not played in browser	Check VoiceAgent logs, browser autoplay policy
    Voice plays but no lip-sync	Audio not sent to D-ID stream	Debug AvatarAgent - same audio must go to D-ID
    D-ID 401	Invalid DID_API_KEY	Regenerate key in D-ID Studio
    D-ID credits exhausted	Free plan limit over	Switch avatar.provider=static fallback
    Avatar generation stuck	Blurry/side-face photo or D-ID processing delay	Use clear front-facing photo, show progress UI
    WebRTC video doesn't load on mobile	Browser/network WebRTC restriction	Test on Chrome Android, keep static fallback
    Mandatory Fallback Test: Disable D-ID intentionally. Static mentor photo + voice must still create a working demo.

12. Common Errors - MediaPipe, Emotion & Sign
    Emotion Detection
    Symptom	Cause	Fix
    Face not detected	Poor light, face too far, camera blocked	Front light, face in center of preview
    Wrong emotion most of the time	Using only one frame's result	Average emotion over last 10 frames
    High CPU usage / lag	Processing every frame at full resolution	Process every 300-500ms, reduce camera resolution
    Sign Language
    Symptom	Cause	Fix
    HELP sign not detected	Landmarks not sent or confidence low	Check SignDetector.tsx & Python /predict-sign logs
    Random signs detected	Confidence threshold too low	Only accept confidence > 0.80
    Python service timeout	Python down or slow model	2s timeout in Java, voice/text must continue
    Hand skeleton not visible	MediaPipe Hands not initialized	Check browser console for WASM loading errors
    Demo Tip: Only demo your 3 most reliable signs live. Don't risk all 10 signs on stage.

13. Common Errors - Python & MCP Server
    Error	Cause	Fix
    ModuleNotFoundError	venv not activated / package missing	Activate venv, pip install -r requirements.txt
    Port 8000 already in use	Old FastAPI process	Kill process, restart uvicorn
    Java can't connect to Python	Wrong PYTHON_SERVICE_URL	Local: http://localhost:8000, Prod: Render URL
    MCP tools not discovered	MCP server down / wrong endpoint	Test with MCP Inspector first
    MCP returns wrong user's mood	userId taken from wrong source	Java must inject userId from JWT only
    MCP timeout	DB query slow / server overloaded	5s timeout + Redis cache + fallback to empty context
    Test Python alone:

bash

curl http://localhost:8000/health
curl http://localhost:8001/health  # MCP if health endpoint added
npx @modelcontextprotocol/inspector python server.py
14. Common Errors - Security & Authentication
    Error	Cause	Fix
    Every API returns 401	JWT filter bug or token not sent	Check Authorization: Bearer header format
    Login works but next API fails	Frontend not storing/sending token	Check localStorage & fetch wrapper
    CORS blocked with credentials	Allowed origin is * or wrong URL	Set exact frontend origin, never * in prod
    User sees another user's mood	Query not filtered by JWT userId	Fix repository query - filter WHERE user_id = jwtUserId always
    Upload avatar fails for valid image	File >5MB or wrong type	Check multipart limits & type whitelist
    WebSocket works locally but not in prod	Using ws:// with https:// site	Use wss:// in production env
15. Deployment Errors
    Symptom	Cause	Fix
    Works on localhost, fails on Render/Vercel	Env variables missing in production	Add all env vars in Render & Vercel dashboards
    Frontend can't reach backend	Wrong NEXT_PUBLIC_API_URL / mixed http-https	Use https:// and wss:// URLs only
    Backend starts then crashes on Render	DB URL/password wrong	Copy Supabase JDBC values exactly
    First request takes 30+ seconds	Render free tier cold start	Open backend health URL 5 mins before demo
    D-ID/Groq works locally, fails in prod	Keys not added in Render	Add GROQ_API_KEY & DID_API_KEY in Render env
16. Performance Debugging
    If total speech-to-avatar time is > 3 seconds, log and check each agent:

Target Latency Budget:
Whisper:        < 500ms
Safety Check:   < 50ms
MCP Context:    < 200ms (cached)
Llama 1st Token:< 800ms
TTS Sentence:   < 1000ms
D-ID Stream:    < 800ms
TOTAL:          < 3 seconds

Debug Process:

Check Java logs for slowest agent.
If Llama is slow -> Check streaming, reduce prompt size.
If TTS is slow -> Check sentence length, split long sentences.
If MCP is slow -> Cache it, never call it per message.
If D-ID is slow -> Use fallback avatar for demo.
Never optimize without measuring first.

17. Error Handling Implementation Checklist
    Backend
    Global Exception Handler returns standard JSON format
    No stack trace is ever sent to frontend
    Every external API call has a timeout
    Safety/Crisis errors bypass Llama and return helpline response
    All agent failures have fallback behavior from 08_AGENT_CONNECTION.md
    Frontend
    Every API failure shows a calm user-friendly message
    Mic/Camera denied shows Text/Sign alternative
    WebSocket disconnect shows reconnect option
    Avatar failure shows static image, not black screen
    401 automatically redirects to Login
18. Demo Day Emergency Playbook
    Keep this checklist open on 2nd Nov 2026.

Emergency	Immediate Action
Backend is sleeping/down	Open /actuator/health to wake it, wait 60 sec
Groq API fails	Retry once, if still failing, play backup demo video
D-ID credits over / avatar fails	Switch config to static avatar + voice mode
Sign detection fails live	Skip sign demo, focus on voice + emotion + avatar
MCP server down	Set MCP_ENABLED=false, restart backend
Internet is slow	Use pre-recorded 2-minute demo video
Camera/Mic not working on demo laptop	Use backup laptop/phone, or play recorded video
Golden Rule for Demo Day: A calm backup video is better than a live crash. Record it on Oct 28 after your Regression Pack passes.

19. Bug Documentation Template
    Add every major bug to BUG_TRACKER.md:

markdown

**Date:** 2026-10-22
**Component:** Avatar / D-ID
**Error:** Audio plays but lip-sync does not work
**Root Cause:** TTS audio bytes were played in frontend but not sent to D-ID stream
**Fix:** Updated AvatarAgent to receive same audio bytes from VoiceAgent
**Tested:** TC-AVT-03 Passed, Golden Flow Passed
**Status:** Closed
This tracker becomes excellent content for your hackathon submission - it shows real engineering process.

20. Definition of Done
    Errors & Debugging setup is complete when:

All services produce clear, structured logs
No API key, password, or JWT appears in any log or frontend
Every Must-Have feature has a tested fallback
Top errors in this document have been tested at least once
Golden Flow passes after every bug fix
Backup demo video is recorded and ready
Demo Day Emergency Playbook is printed / saved offline