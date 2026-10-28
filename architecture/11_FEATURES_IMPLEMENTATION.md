FEATURES IMPLEMENTATION DOCUMENT
Project: Saarthi AI - AI Mentor Document Version: 1.0 Author: Shreynsh Singh (Solo Developer) Hackathon: Meta Global AI Developer Hackathon Deadline: 2nd November 2026 Related Docs: PRD.md, ARCHITECTURE.md, 07_API_INTEGRATION.md, 08_AGENT_CONNECTION.md, 09_PROJECT_IMPLEMENTATION.md

1. Purpose
   This document defines how each feature of Saarthi AI will be implemented — module by module, file by file, API by API.

It answers for every feature:

What to build
Where to build it (Frontend / Java / Python / External API)
What depends on it
When it's considered DONE
2. Feature Implementation Summary
   ID	Feature	Priority	Phase	Est. Time (Solo)	Depends On
   F-01	User Authentication	Must Have	Phase 1	1 Day	DB Setup
   F-02	Text Mentoring (Llama)	Must Have	Phase 1	2 Days	F-01
   F-03	Speech-to-Text (Voice Input)	Must Have	Phase 2	1 Day	F-02
   F-04	Text-to-Speech (Voice Output)	Must Have	Phase 2	1 Day	F-02
   F-05	Speech-to-Speech Live Session	Must Have	Phase 2	2 Days	F-03, F-04
   F-06	Facial Emotion Detection	Must Have	Phase 3	2 Days	F-05
   F-07	Emotion-Aware Mentoring	Must Have	Phase 3	1 Day	F-06, F-02
   F-08	Live AI Avatar with Lip-Sync	Must Have	Phase 3	2 Days	F-04
   F-09	Personalized Avatar from Photo	Must Have	Phase 3	1 Day	F-08
   F-10	Sign Language Recognition	Should Have	Phase 4	3 Days	F-05, Python Service
   F-11	Mood Tracking & Dashboard	Should Have	Phase 4	1 Day	F-02, F-06
   F-12	Crisis Detection & Safety	Must Have	Phase 4	1 Day	F-02
   F-13	Multilingual Support (Hindi/Hinglish)	Should Have	Phase 2	Integrated	F-03, F-04
   Implementation Rule: Implement in Phase order. Never start F-08 (Avatar) before F-05 (Voice) is working.

mermaid

graph TD
F01[F-01 Auth] --> F02[F-02 Text Mentor]
F02 --> F03[F-03 STT]
F02 --> F04[F-04 TTS]
F03 --> F05[F-05 Speech-to-Speech]
F04 --> F05
F05 --> F06[F-06 Emotion Detection]
F06 --> F07[F-07 Emotion-Aware Reply]
F04 --> F08[F-08 Live Avatar]
F08 --> F09[F-09 Custom Avatar]
F05 --> F10[F-10 Sign Language]
F02 --> F11[F-11 Mood Dashboard]
F02 --> F12[F-12 Crisis Safety]
3. Feature Implementation Details
   F-01: User Authentication
   Goal: Secure registration/login so each user's conversations, mentor, and mood history are private.

Frontend Implementation:

Pages: app/login/page.tsx, app/register/page.tsx
Store JWT in localStorage, attach to all API + WebSocket calls
Redirect unauthenticated users from /dashboard and /session to /login
Backend Implementation:

Classes: controller/AuthController.java, config/SecurityConfig.java, service/JwtService.java
Passwords hashed with BCrypt, JWT expiry 24h for hackathon
Endpoints: POST /api/v1/auth/register, POST /api/v1/auth/login
Database: users table

Done When:

User can register and login
Wrong password returns 401
Protected APIs fail without JWT
F-02: Text Mentoring with Llama
Goal: The core brain. User types, Saarthi replies like a mentor, not a generic chatbot. This is the foundation for all other features.

Frontend Implementation:

Component: app/session/page.tsx - chat input + streaming text display
Connect via WebSocket, render tokens as they arrive
Backend Implementation:

Classes: agent/core/MentorBrainAgent.java, service/LlamaService.java, handler/MentorWebSocketHandler.java
Load System Prompt from 03_MENTOR_SYSTEM_PROMPT.md - never hardcode a generic prompt
Call Groq: model llama-3.3-70b-versatile, stream: true, temperature: 0.7
Parse SSE chunks and forward token-by-token to frontend
External API: Groq API POST /openai/v1/chat/completions

Database: Save to conversations + messages tables

Done When:

User types "I am stressed about exams" in Hinglish, mentor replies in Hinglish in 2-3 short sentences
First token appears in < 800ms
Conversation is saved in PostgreSQL
F-03: Speech-to-Text (Voice Input)
Goal: User speaks in English/Hindi/Hinglish, system converts to text accurately.

Frontend Implementation:

Component: components/VoiceRecorder.tsx
Use browser MediaRecorder API, record as webm, send audioBase64 via WebSocket
UI states: Mic idle -> Pulsing "Listening..." -> Processing
Backend Implementation:

Class: agent/perception/SpeechAgent.java
Send audio to Groq Whisper, return transcribed text to Orchestrator
External API: Groq whisper-large-v3 - auto-detects language, no manual language selection needed

Done When:

Speaking Hindi, English, and Hinglish all transcribe correctly
Transcription latency < 500ms
Failure shows: "I couldn't hear that clearly. Take your time, I'm here."
F-04: Text-to-Speech (Voice Output)
Goal: Mentor's text reply is spoken in a natural human voice.

Frontend Implementation:

Play incoming audioBase64 chunks in order
Show live subtitles synced with audio playback
Backend Implementation:

Class: agent/action/VoiceAgent.java
Critical Implementation Detail: Buffer Llama's streamed tokens until a full sentence (. / ।) is complete, THEN send to TTS. Never send half-sentences.
Voices: en-IN-NeerjaNeural (Female), hi-IN-SwaraNeural (Hindi)
External API: Microsoft Edge-TTS (Free, no key needed)

Done When:

Mentor reply is heard as clear audio
Audio generation per sentence < 1.5s
Subtitles match spoken audio
F-05: Speech-to-Speech Live Session
Goal: Combine F-03 + F-02 + F-04 into one seamless video-call-like experience. This is your core demo screen.

Frontend Implementation:

Page: app/session/page.tsx as per 05_UI_UX_DESIGN.md Section 7.3
One WebSocket connection per session: ws://localhost:8080/ws/mentor?token=<JWT>
Control bar: [Mic] [Text] [Sign] [End Session]
Backend Implementation:

Class: agent/orchestrator/MentorOrchestrator.java - connects Speech -> Safety -> Memory -> Brain -> Voice in this exact order
Full flow defined in 08_AGENT_CONNECTION.md Section 5
Done When:

User speaks -> Avatar/Text replies with voice in total < 3 seconds
User can switch to typing mid-session without reconnecting
Ending session saves full conversation
F-06: Facial Emotion Detection
Goal: Detect user's real emotion from webcam - privately.

Frontend Implementation (100% Browser, No Backend):

Component: components/EmotionDetector.tsx
Use MediaPipe Face Mesh (WASM) to get face landmarks
Derive label: HAPPY / SAD / STRESSED / ANXIOUS / TIRED / NEUTRAL + confidence
Show live badge in UI: Emotion: STRESSED (87%)
Send ONLY the label + confidence via WebSocket. Raw video frames never leave the browser.
Backend Implementation:

No video processing. Java only receives emotion string in AgentContext DTO.
Done When:

Emotion badge updates live as expression changes
Blocking camera falls back to emotion = NEUTRAL and chat still works
Network tab proves no video is sent to server
F-07: Emotion-Aware Mentoring
Goal: Mentor reacts to how user looks, not just what they say. This is your differentiator from ChatGPT.

Implementation (No new files - logic inside prompt + orchestrator):

MentorOrchestrator injects emotion into Llama context: "User Emotion: STRESSED\nUser says: I'm fine"
System Prompt instructs Llama to gently acknowledge mismatch, without saying "I detected via camera"
Example:

User says "I'm fine" + Emotion = SAD -> Mentor: "You said you're fine, but you seem a bit low today. Want to share what's on your mind?"

Done When:

The above example works live in demo
Mentor never uses robotic phrases like "As an AI, I detected..."
F-08: Live AI Avatar with Lip-Sync
Goal: A face that talks back with lip movements matching the TTS audio.

Frontend Implementation:

Component: components/AvatarPlayer.tsx
Render D-ID WebRTC video stream in a large rounded-3xl aspect-video container
Add gentle pulsing ring when avatar is speaking
Backend Implementation:

Interface: agent/action/AvatarAgent.java
Implementation: service/DidAvatarServiceImpl.java (swap to MuseTalk later without changing other code)
Flow: TTS audio -> D-ID stream -> WebRTC video back to frontend
External API: D-ID Streaming API POST /talks/streams

Fallback Implementation (Mandatory): If D-ID credits finish or API fails, show static mentor photo + play audio only. Session must never crash.

Done When:

Avatar speaks with lip-sync matching audio
Fallback to static image works when D-ID is disabled in config
F-09: Personalized Avatar from Photo Upload
Goal: The emotional wow feature. User's trusted person's photo becomes their mentor.

Frontend Implementation:

Page: app/mentor/create/page.tsx
Upload UI with guidelines: "Clear, front-facing photo, good light"
Show progress steps: Uploading -> Creating Mentor -> Adding Voice -> Ready
Backend Implementation:

Endpoint: POST /api/v1/mentor/upload-avatar (multipart/form-data)
Class: controller/MentorController.java
Save file, call D-ID to create presenter, save returned ID
Database: mentor_profiles table (avatar_image_url, avatar_provider_id, voice_id)

Done When:

Uploading 1 photo creates a talking avatar in < 60 seconds
That same avatar appears in the next live session
F-10: Sign Language Recognition
Goal: Deaf/mute users can communicate with Saarthi. Implement LAST, keep scope small.

MVP Scope (10 signs only): Hello, Thank You, Yes, No, Please, Sad, Happy, Stress, Help, I Need Help

Frontend Implementation:

Component: components/SignDetector.tsx
MediaPipe Hands extracts 21 hand landmarks (x, y, z), sends array via WebSocket every 500ms
Show hand skeleton overlay on camera preview + detected sign as text
Python Implementation:

Service: ai-service-python/main.py
Endpoint: POST /predict-sign - receives landmarks, returns {sign: "HELP", confidence: 0.94}
Backend Implementation:

Client: agent/perception/SignAgentClient.java with 2-second timeout
If confidence > 0.80, treat sign label as user text and pass to Llama
Done When:

Showing HELP sign sends a HELP message to mentor
Turning off Python service does NOT break voice/text chat
F-11: Mood Tracking & Dashboard
Goal: User sees their emotional journey and feels progress.

Frontend Implementation:

Pages: app/dashboard/page.tsx, app/mood/page.tsx
Simple 7-day line/bar chart, no complex medical graphs
Insight card: Llama-generated summary of mood pattern
Backend Implementation:

Classes: agent/core/MemoryAgent.java, controller/MoodController.java
Endpoint: GET /api/v1/mood/history?days=7
After every session, save detected emotion to mood_logs
Database: mood_logs table with index on (user_id, logged_at)

Done When:

After a chat, today's mood appears on dashboard chart
Dashboard greeting uses last mood: "You felt stressed yesterday. Want to talk today?"
F-12: Crisis Detection & Safety
Goal: Responsible AI. Protect vulnerable users. Judges will test this.

Backend Implementation:

Class: agent/core/SafetyAgent.java
Runs BEFORE Llama on every message - keyword check + intent rules
On trigger: bypass Llama completely, return fixed CRISIS_ALERT WebSocket message
Frontend Implementation:

Calm modal (soft colors, NOT alarming red) from 05_UI_UX_DESIGN.md Section 7.6
Buttons: Call Tele-MANAS: 14416, Call AASRA: 9820466726
Fixed Response:

"I'm really glad you told me this. Please don't go through this alone. You can call Tele-MANAS at 14416 (24x7) right now, and please reach out to a person you trust near you. I'm here to listen."

Done When:

Typing a self-harm phrase triggers helpline modal in < 1 second
Normal mentoring does NOT continue after crisis trigger
Disclaimer "Saarthi is a Mentor, not a medical therapist" is visible in app
F-13: Multilingual Support
Goal: Speak to user in their own language without any settings.

Implementation (No separate module - built into F-03/F-04):

Whisper auto-detects Hindi/English/Hinglish
System Prompt rule: "Reply in the SAME language the user uses"
TTS voice auto-selected based on detected language
Done When:

User speaks Hinglish -> Mentor replies in Hinglish
User switches to English mid-conversation -> Mentor switches too
4. Features Explicitly NOT Implemented in MVP
   To protect your solo deadline, these are documented as V2 in the PRD and must NOT be coded before 2nd Nov:

Full ISL/ASL sentence translation (only 10 static signs in MVP)
Medical diagnosis, therapy plans, or medicine suggestions
Payments, subscriptions, native mobile apps
Self-hosted avatar model training (use D-ID API)
Group/community features and long-term vector memory
5. Feature Acceptance Checklist (Final Demo Test)
   Your feature implementation is complete when a judge can do this live, end-to-end, on the deployed link:

Register/Login (F-01)
Upload a photo and generate a mentor (F-09)
Start live session and type a message (F-02)
Speak in Hinglish and get a spoken reply (F-03, F-04, F-05, F-13)
See live emotion badge change with facial expression (F-06)
Get an emotion-aware reply when saying "I'm fine" while looking sad (F-07)
See the uploaded avatar talk with lip-sync (F-08)
Show HELP sign and get a response (F-10)
End session and see mood on dashboard (F-11)
Trigger crisis flow and see helplines (F-12)
