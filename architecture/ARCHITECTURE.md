# SYSTEM ARCHITECTURE DOCUMENT

**Project:** Saarthi AI - The AI Mentor
**Document Version:** 1.0
**Author:** Shreynsh Singh (Solo Developer & Architect)
**Date:** 2025-2026
**Hackathon:** Meta Global AI Developer Hackathon
**Deadline:** 2nd November 2026
**Status:** Finalized for Development

---

## 1. Introduction

### 1.1 Purpose
This document defines the complete technical architecture of Saarthi AI, a real-time multimodal AI Mentor for students and professionals facing stress, anxiety, burnout, and lack of life clarity.

### 1.2 Problem We Solve
Existing AI is text-only and emotion-blind. Saarthi is designed to SEE (facial expressions), HEAR (voice), UNDERSTAND (sign language), and SPEAK BACK (live personalized avatar) in real-time.

### 1.3 Architectural Goal
Build a system that feels like a <3 second real-time human video call, while being scalable, privacy-first, and buildable by a solo developer.

## 2. Architecture Principles

1.  **Microservices-Lite:** Don't build a monolith. Separate concerns: Java for business logic & scale, Python for AI, Next.js for realtime UI.
2.  **Privacy By Design:** Raw camera video NEVER leaves the user's browser. Only metadata (`emotion: STRESSED`) is sent to the server.
3.  **Streaming Everything:** No request waits for a full response. Llama -> TTS -> Avatar must stream sentence-by-sentence to achieve low latency.
4.  **API-First for AI:** Use managed APIs (Groq, D-ID) for MVP to focus on product, not model training. Architecture allows swapping them later.
5.  **Safety First:** Crisis detection is not a feature, it's a pre-processing gate before Llama.

## 3. System Context Diagram (C4 Level 1)


graph TD
    User([User<br/>Student / Professional / Deaf User])
    User --> Saarthi[Saarthi AI System]
    
    Saarthi --> Groq[Groq API<br/>Llama 3.3 70B + Whisper]
    Saarthi --> AvatarAPI[D-ID Streaming API<br/>Live Avatar Engine]
    Saarthi --> TTS[Edge-TTS / ElevenLabs<br/>Text-to-Speech]
    Saarthi --> DB[(PostgreSQL + Redis)]
    
    

## 4. Container Diagram (C4 Level 2)
This is our core Hybrid Architecture.
graph TB
    subgraph Client["Client - User Browser"]
        FE[Next.js Frontend<br/>React + Tailwind + MediaPipe]
    end

    subgraph Backend["Backend Services"]
        JAVA[Java Backend - Core<br/>Spring Boot 3.2 + WebFlux<br/>WebSocket Server, Auth, Logic]
        PY[Python AI Service<br/>FastAPI<br/>Sign Language Classifier]
    end

    subgraph Data["Data Layer"]
        PG[(PostgreSQL 16<br/>Users, Chats, Mood Logs)]
        RD[(Redis<br/>Session, Rate Limit, Cache)]
    end

    subgraph External["External AI APIs"]
        LLAMA[Llama via Groq]
        WHISPER[Whisper-large-v3]
        DID[D-ID / MuseTalk]
        EDGE[Edge-TTS]
    end

    FE <-->|WSS - WebSocket JSON + Audio Chunks| JAVA
    FE -->|HTTPS - Auth, Upload, Dashboard| JAVA
    
    JAVA <-->|REST / Internal HTTP| PY
    JAVA --> PG
    JAVA --> RD
    
    JAVA -->|Streaming Chat Completion| LLAMA
    JAVA -->|Audio File| WHISPER
    JAVA -->|Text + Audio Stream| DID
    JAVA --> EDGE
    DID -->|WebRTC Video Stream| FE

4.1 Container Responsibilities
Container	Tech Stack	Responsibility
Frontend	Next.js 14, TypeScript, Tailwind CSS, MediaPipe JS	UI, Webcam/Mic access, In-browser Emotion & Hand Landmark detection, Avatar video rendering
Java Backend	Java 21, Spring Boot 3.2, WebFlux, Spring Security	Core orchestrator, WebSocket gateway, Auth (JWT), Llama/TTS/Avatar orchestration, Crisis detection, DB persistence
Python Service	Python 3.11, FastAPI, TensorFlow/Scikit-learn	Receives hand landmarks, classifies into ISL signs (Hello, Help, Sad). Kept separate so Java stays clean.
Database	PostgreSQL + Redis	PostgreSQL for permanent data, Redis for live WebSocket sessions & caching avatar IDs
5. Component Diagram - Java Backend
mermaid

graph LR
    subgraph SpringBoot["Spring Boot Application"]
        WS[MentorWebSocketHandler] --> CS[CrisisDetectionService]
        WS --> LS[LlamaService]
        WS --> TS[TtsService]
        WS --> AS[AvatarService]
        WS --> MS[MoodService]
        
        AC[AuthController] --> SEC[Spring Security + JWT]
        MC[MentorController] --> AS
        MDC[MoodController] --> MS
    end
    LS --> GROQ[Groq API Client - WebClient]
6. Runtime Sequence Diagrams
6.1 Speech-to-Speech Conversation Flow (Core Flow)
mermaid

sequenceDiagram
    participant U as User
    participant FE as Next.js (MediaPipe)
    participant JAVA as Spring Boot (WS)
    participant W as Whisper
    participant L as Llama 3.3
    participant T as TTS + D-ID Avatar
​
    U->>FE: Speaks "I am stressed about exams"
    FE->>FE: Detects Emotion: STRESSED (0.87)
    FE->>JAVA: Sends Audio Chunk + {emotion: STRESSED}
    JAVA->>JAVA: Crisis Check - PASS
    JAVA->>W: Send Audio
    W-->>JAVA: Text: "I am stressed about exams"
    JAVA->>L: System Prompt + Emotion + Text
    L-->>JAVA: Streams: "Samajh sakta hoon..."
    JAVA->>T: Sentence 1 to TTS
    T-->>FE: Audio + Lip-Sync Video Stream
    FE->>U: Avatar Speaks Back
    JAVA->>JAVA: Save Message + MoodLog to PostgreSQL
6.2 Personalized Avatar Generation Flow
User uploads photo -> POST /api/mentor/upload to Java
Java stores image in /uploads (S3 in prod) and calls AvatarService.createPresenter(imageUrl)
D-ID returns presenter_id
Java saves presenter_id in mentor_profiles table
During live session, Java creates D-ID stream with that presenter_id
6.3 Sign Language Flow
MediaPipe Hands in browser extracts 21 hand landmarks (x,y,z)
Frontend sends landmarks array via WebSocket every 500ms
Java forwards to Python POST /predict-sign
Python LSTM model returns {sign: "HELP", confidence: 0.94}
Java treats "HELP" as a user message and sends to Llama
7. Data Architecture
mermaid

erDiagram
    USERS ||--o{ MENTOR_PROFILES : has
    USERS ||--o{ CONVERSATIONS : starts
    USERS ||--o{ MOOD_LOGS : logs
    CONVERSATIONS ||--o{ MESSAGES : contains
​
    USERS { UUID id PK; string email UK; string password_hash; string preferred_language }
    MENTOR_PROFILES { UUID id PK; UUID user_id FK; string avatar_image_url; string avatar_provider_id; string voice_id }
    CONVERSATIONS { UUID id PK; UUID user_id FK; string title; timestamp started_at }
    MESSAGES { UUID id PK; UUID conversation_id FK; string role; text content; string detected_emotion }
    MOOD_LOGS { UUID id PK; UUID user_id FK; string mood; float confidence; timestamp logged_at }
Full SQL Schema: Refer to 01_ARCHITECTURE.md / PRD Appendix. Uses PostgreSQL ENUMs for mood_type and indexes on user_id + logged_at for fast dashboard queries.

8. Communication Protocols
8.1 WebSocket API (/ws/mentor)
All real-time communication uses a single persistent WSS connection.

Client -> Server (User Message):

json

{
  "type": "USER_MESSAGE",
  "conversationId": "uuid",
  "inputMode": "VOICE | TEXT | SIGN",
  "text": "I am feeling stressed",
  "emotion": "STRESSED",
  "emotionConfidence": 0.87,
  "audioBase64": "..." 
}
Server -> Client (Mentor Response - Streamed):

json

{
  "type": "MENTOR_RESPONSE_CHUNK",
  "textChunk": "Samajh sakta hoon, ",
  "audioUrl": "/audio/chunk1.mp3",
  "isFinal": false
}
8.2 REST APIs
Method	Endpoint	Description
POST	/api/auth/register	Create account
POST	/api/auth/login	Returns JWT
POST	/api/mentor/upload-avatar	Upload photo, create D-ID avatar
GET	/api/mood/history?days=7	Get mood graph data
GET	/api/conversations	List past chats
9. External Integrations
Service	Purpose	Fallback Plan
Groq API	Llama 3.3 70B inference + Whisper STT	Together.ai API (OpenAI-compatible)
D-ID	Live talking avatar with lip-sync	MuseTalk open-source model on Python service + static image fallback
Edge-TTS	Free, fast Hindi/English TTS	ElevenLabs API for higher quality demo voice

10. Security Architecture
Authentication: Spring Security + JWT (Access Token 15min, Refresh 7 days). Passwords hashed with BCrypt(12).
Transport: HTTPS + WSS only in production.
Privacy: Video frames processed via MediaPipe WASM in-browser. Server never receives/stores video.
Input Safety: CrisisDetectionService checks a keyword + Llama classification layer before every prompt. If triggered, bypass Llama and return fixed helpline response.
CORS: Strictly allow only Vercel frontend domain.

11. Scalability & Performance Strategy
Latency Budget (Target < 3s): STT (Whisper) 400ms + Llama First Token 500ms + TTS 600ms + Avatar Stream 800ms = ~2.3s

How we scale:

Spring WebFlux (non-blocking) handles 1000s of concurrent WebSockets on one instance vs. blocking Tomcat threads.
Redis stores WebSocket session mapping, allowing us to run 2+ Java instances behind a Load Balancer later.
Llama/TTS are stateless external APIs, so our backend stays lightweight.
PostgreSQL connection pooling via HikariCP.


12. Deployment Architecture
mermaid

graph TD
    User --> Vercel[Vercel<br/>Next.js Frontend]
    Vercel --> Render[Render / Railway<br/>Docker: Java Backend]
    Render --> Supabase[Supabase<br/>Managed PostgreSQL]
    Render --> Upstash[Upstash<br/>Managed Redis]
    Render --> PythonSvc[Render<br/>Docker: Python FastAPI]
CI/CD: GitHub Actions -> Build Docker image for Java (./mvnw package) -> Deploy to Render.
Environments: dev (local docker-compose), prod (deployed URLs in Vercel env vars).
Docker Compose (Local): Contains postgres:16, redis:7, backend-java, ai-python.
13. Architecture Decision Records (ADR)
#	Decision	Reason
ADR-01	Use Java for main backend, not Node/Python monolith	You required scalability; Spring WebFlux is proven for high-concurrency WebSockets & enterprise security.
ADR-02	MediaPipe in browser, not server-side CV	100% privacy, zero server GPU cost, lower latency.
ADR-03	Use D-ID API instead of training Wav2Lip	Solo developer + deadline. Training avatar model would take months. API gives production quality in 1 day.
ADR-04	Groq for Llama hosting	Fastest inference (<500ms first token) and OpenAI-compatible API makes integration in Java trivial via WebClient.
14. Risks & Trade-offs
Risk	Mitigation in Architecture
D-ID API credits expire	AvatarService is an interface. Can swap to MuseTalk implementation without changing WebSocket handler.
Python service becomes bottleneck	It's only for sign classification (lightweight). Can be merged into frontend TF.js model in V2.
High WebSocket latency on free hosting	Keep a recorded backup demo video. Deploy backend to a region close to Groq (US).
15. Future Evolution (V2)
Replace D-ID with self-hosted MuseTalk for zero cost.
Add long-term memory using Vector DB (pgvector in PostgreSQL) so Saarthi remembers user's goals.
Extract Sign Language into a standalone microservice with full ISL sentence translation.
Native mobile app (React Native) reusing the same Java WebSocket backend.
