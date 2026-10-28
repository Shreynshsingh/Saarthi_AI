PROJECT IMPLEMENTATION DOCUMENT
Project: Saarthi AI - AI Mentor Version: 1.0 Author: Shreynsh Singh (Solo Developer) Hackathon: Meta Global AI Developer Hackathon Deadline: 2nd November 2026 Status: Ready for Implementation

1. Implementation Objective
   To implement a real-time, multimodal AI Mentor that can see facial expressions, hear voice, understand sign language, and reply through a live personalized AI avatar - using a scalable Java + Python + Next.js hybrid architecture.

This document is the execution plan for: PRD.md + ARCHITECTURE.md + UI_UX_DESIGN.md + API_INTEGRATION.md + AGENT_CONNECTION.md

2. Implementation Strategy
   As a solo developer, we follow Vertical Slice Implementation - not horizontal.

Don't do this: Build complete frontend, then complete backend, then AI. Do this: Build ONE end-to-end flow at a time.

Slice 1: Text -> Llama -> Text Reply (Working Mentor)
Slice 2: Voice -> Whisper -> Llama -> TTS -> Voice Reply
Slice 3: Voice + Emotion -> Llama -> Avatar Speaks
Slice 4: + Sign Language + Custom Avatar + Safety

Each slice must be demoable. If time runs out, you can submit the last completed slice.

3. Final Tech Stack To Implement
   Layer	Technology	Implementation Responsibility
   Frontend	Next.js 14, TypeScript, Tailwind CSS	UI, Camera/Mic, MediaPipe, WebSocket Client, Avatar Player
   Backend	Java 21, Spring Boot 3.2, WebFlux, WebSocket	Orchestrator, Auth, Llama/Whisper/TTS/D-ID Integration, DB
   AI Service	Python 3.11, FastAPI	Sign Language Classification only
   LLM	Llama 3.3 70B via Groq	Mentor Brain
   STT	Groq Whisper-large-v3	Speech to Text
   TTS	Microsoft Edge-TTS	Text to Speech
   Avatar	D-ID Streaming API	Live Lip-Sync Avatar
   Vision	MediaPipe Face Mesh + Hands	Emotion + Sign Landmarks in Browser
   Database	PostgreSQL 16 + Redis 7	Permanent Data + Session Cache
   Deployment	Vercel + Render + Supabase	Production Hosting
4. Repository Structure To Create
   saarthi-ai/
   ├── frontend-nextjs/
   │   ├── app/
   │   │   ├── page.tsx              # Landing
   │   │   ├── dashboard/page.tsx
   │   │   ├── session/page.tsx      # CORE: Live Mentor Screen
   │   │   └── mentor/create/page.tsx
   │   ├── components/
   │   │   ├── AvatarPlayer.tsx
   │   │   ├── EmotionDetector.tsx   # MediaPipe Face
   │   │   ├── SignDetector.tsx      # MediaPipe Hands
   │   │   └── VoiceRecorder.tsx
   │   └── lib/websocket.ts
   ├── backend-java/
   │   └── src/main/java/com/saarthi/ai/
   │       ├── config/               # WebSocket, Redis, Security, WebClient
   │       ├── controller/           # Auth, Mentor, Mood
   │       ├── handler/              # MentorWebSocketHandler
   │       ├── agent/                # Orchestrator + All Agents
   │       ├── service/
   │       ├── model/                # JPA Entities
   │       ├── repository/
   │       └── dto/
   ├── ai-service-python/
   │   ├── main.py                   # FastAPI App
   │   ├── sign_model.py
   │   └── requirements.txt
   ├── docs/                         # All 9 documents
   └── docker-compose.yml

5. Environment Setup (Day 1)
   5.1 Prerequisites
   Java 21, Maven, Node.js 18+, Python 3.11, Docker Desktop, Git

5.2 .env Variables Required
env

GROQ_API_KEY=
DID_API_KEY=
JWT_SECRET=
DB_HOST=localhost
DB_NAME=saarthi_db
DB_USERNAME=saarthi_user
DB_PASSWORD=saarthi_password
REDIS_HOST=localhost
PYTHON_SERVICE_URL=http://localhost:8000
FRONTEND_URL=http://localhost:3000
5.3 Local Run Order
bash

1. docker-compose up -d          # Postgres + Redis
2. cd backend-java && ./mvnw spring-boot:run   # Port 8080
3. cd ai-service-python && uvicorn main:app --port 8000
4. cd frontend-nextjs && npm run dev           # Port 3000
   Validation: GET http://localhost:8080/actuator/health returns db: UP, redis: UP

6. Module-Wise Implementation Plan
   Module 1: Backend Foundation
   Files: SaarthiApplication.java, application.yml, SecurityConfig.java Tasks:

Create Spring Boot project via start.spring.io with WebFlux, WebSocket, JPA, Security, Redis
Configure PostgreSQL + Flyway V1__init_schema.sql (from 06_DATABASE_CONNECTION.md)
Implement JWT Auth: /register and /login Done When: User can register, login, and get JWT.
Module 2: Mentor Brain Agent (Highest Priority)
Files: agent/core/MentorBrainAgent.java, LlamaService.java Tasks:

Configure WebClient for https://api.groq.com/openai/v1
Load System Prompt from 03_MENTOR_SYSTEM_PROMPT.md
Implement streaming streamMentorResponse(text, emotion, moodHistory)
Parse SSE chunks from Groq Done When: Postman sends "I am stressed" -> Llama streams empathetic Hinglish reply.
Module 3: WebSocket Orchestrator
Files: handler/MentorWebSocketHandler.java, agent/orchestrator/MentorOrchestrator.java, config/WebSocketConfig.java Tasks:

Create /ws/mentor endpoint
Orchestrator flow: Input -> Safety -> Memory -> Brain -> Output
Implement message types: USER_MESSAGE, MENTOR_RESPONSE_CHUNK, CRISIS_ALERT Done When: Frontend connects via WebSocket and gets token-by-token reply.
Module 4: Speech Agents (Voice In + Voice Out)
Files: agent/perception/SpeechAgent.java, agent/action/VoiceAgent.java Tasks:

SpeechAgent: Send audio bytes to Groq Whisper, get text
VoiceAgent: Send Llama sentence to Edge-TTS, get MP3 bytes
Implement sentence buffering - don't send half sentences to TTS Done When: User speaks Hindi/English -> Mentor speaks back.
Module 5: Frontend Live Session (Core UI)
Files: app/session/page.tsx, components/AvatarPlayer.tsx Tasks:

Build UI as per 05_UI_UX_DESIGN.md Section 7.3 (Video call layout)
Implement microphone recording, WebSocket send/receive, live subtitles
Implement states: Listening, Thinking, Speaking Done When: Live Session screen looks like Google Meet and text chat works.
Module 6: Emotion Agent
Files: components/EmotionDetector.tsx Tasks:

Integrate MediaPipe Face Mesh in browser
Derive emotion label: HAPPY / SAD / STRESSED / TIRED / NEUTRAL
Send only label + confidence with every WebSocket message. Never send video. Done When: UI shows live badge Emotion: STRESSED (87%) and Llama reacts to it.
Module 7: Avatar Agent
Files: agent/action/AvatarAgent.java, service/AvatarService.java Tasks:

Create interface AvatarAgent with implementation DidAvatarServiceImpl
Implement POST /api/v1/mentor/upload-avatar -> Call D-ID -> Save avatar_provider_id
Connect TTS audio output to D-ID stream for lip-sync
Fallback: If D-ID fails, return static image + audio only Done When: Uploaded photo talks back with lip-sync.
Module 8: Sign Language Agent
Files: ai-service-python/main.py, components/SignDetector.tsx, agent/perception/SignAgentClient.java Tasks:

Frontend: MediaPipe Hands extracts 21 landmarks
Python: POST /predict-sign classifies landmarks for 10 signs
Java: If confidence > 0.80, treat sign as user text Priority: Implement LAST. Limit to Hello, Help, Sad, Happy, Stress, Thank You. Done When: Showing HELP sign sends "HELP" message to Mentor.
Module 9: Safety & Memory Agents
Files: agent/core/SafetyAgent.java, agent/core/MemoryAgent.java Tasks:

SafetyAgent: Keyword check before Llama. On crisis, return Tele-MANAS 14416 response.
MemoryAgent: Fetch last 3 moods from PostgreSQL, save new message + mood_log after each reply
Build GET /api/v1/mood/history for dashboard chart Done When: Typing self-harm phrase triggers CRISIS_ALERT and mood appears on dashboard.
7. Implementation Timeline
   mermaid

gantt
title Saarthi AI Solo Implementation Timeline
dateFormat  YYYY-MM-DD
section Phase 1 Foundation
Setup & DB & Auth           :a1, 2026-10-05, 5d
Llama Text Mentor           :a2, after a1, 4d
section Phase 2 Voice
Whisper + TTS               :a3, after a2, 4d
WebSocket Streaming         :a4, after a3, 3d
section Phase 3 Avatar & Vision
Emotion Detection           :a5, after a4, 3d
D-ID Avatar + Upload        :a6, after a5, 4d
section Phase 4 Advanced
Sign Language               :a7, after a6, 3d
Safety + Mood Dashboard     :a8, after a7, 3d
section Final
Testing & Deployment        :a9, after a8, 4d
Demo Video & Submission     :milestone, 2026-11-02, 0d
Phase	Duration	Deliverable
Phase 1: Foundation	Day 1-5	DB connected, Auth works, Text Mentor replies
Phase 2: Voice	Day 6-9	Speech-to-Speech conversation works
Phase 3: Avatar + Vision	Day 10-16	Full Magic Demo: Emotion + Talking Avatar
Phase 4: Polish & Safety	Day 17-21	Sign (basic), Crisis flow, Mood dashboard
Phase 5: Final Week	Oct 27 - Nov 2	Deploy, Test, Record Demo, Submit
Rule: Freeze new features after Day 21. Only bug fixes after that.

8. Git & Coding Standards
   Branch Strategy (Solo): main (stable) -> dev (daily work) -> feature branches: feat/llama-stream, feat/avatar-did

Commit Convention:

feat(backend): implement Llama streaming service
fix(websocket): handle audio chunk parsing
docs: add implementation document

Java Standards: Use Lombok, DTO Records, Constructor Injection (@RequiredArgsConstructor), never field injection. Frontend Standards: TypeScript strict mode, components in PascalCase, all API calls only to Java backend.

9. Integration & Testing Order
   Implement and test in this exact order to avoid debugging hell:

DB Health Check -> 2. Auth API (Postman) -> 3. Llama Text (Postman) -> 4. WebSocket Text (wscat) -> 5. Frontend Text Chat -> 6. Whisper -> 7. TTS -> 8. Emotion Label -> 9. D-ID Avatar -> 10. Sign Python Service
Never test all agents together first. Test one agent, lock it, move to next (as per 08_AGENT_CONNECTION.md).

10. Key Implementation Risks
    Risk	Implementation Mitigation
    D-ID credits finish	AvatarAgent is an interface. Switch config saarthi.agents.avatar.provider: static
    Latency > 5 seconds	Stream Llama sentence-by-sentence. Log [Agent Name] Latency: Xms for every step.
    Python service crashes	Wrap SignAgentClient with 2s timeout. Voice/Text must continue without it.
    Groq API downtime	Add Together.ai as fallback base-url in application.yml, same OpenAI format.
    Solo burnout / scope creep	Follow Vertical Slice rule. Sign language is Should-Have, not Must-Have for core demo.
11. Deployment Implementation
    Database: Create Supabase project, copy JDBC URL to Render env vars.
    Backend: Create Dockerfile in backend-java, deploy to Render. Set all .env vars in Render dashboard.
    Python: Deploy ai-service-python as separate Render service.
    Frontend: Connect GitHub repo to Vercel, set NEXT_PUBLIC_WS_URL=wss://your-backend.onrender.com/ws/mentor
    Final Check: Update CORS in Spring Security to allow only Vercel domain.
    Detailed steps will be in DEPLOYMENT_GUIDE.md.

12. Definition of Done (Final Acceptance)
    Implementation is complete when a new user can do this live without errors:

Register/Login in < 30 seconds
Upload photo and generate mentor avatar in < 60 seconds
Start live session, camera shows emotion badge
Speak in Hinglish, avatar replies in Hinglish with lip-sync in < 3 seconds
Show HELP sign, system understands it
End session, mood is visible on dashboard
Crisis phrase triggers helpline modal instantly
If all 7 pass on the deployed Vercel + Render link, you are ready to submit.