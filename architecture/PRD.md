# PROJECT REQUIREMENT DOCUMENT (PRD)

**Project Name:** Saarthi AI - AI Mentor
**Version:** 1.0
**Author:** Shreynsh Singh (Solo Developer)
**Date:** Prepared for Meta Global AI Developer Hackathon
**Deadline:** 2nd November 2026
**Status:** Approved for Development

---

## 1. Executive Summary

Saarthi AI is a real-time, multimodal AI Mentor designed to help students,
working professionals, and individuals struggling with stress, anxiety,
lack of clarity, and emotional overwhelm.

Unlike a text chatbot, Saarthi can SEE the user (facial expressions),
HEAR the user (speech-to-speech), UNDERSTAND sign language, and
TALK BACK through a live, personalized AI Avatar with lip-sync.

The user's own uploaded photo (of a teacher, parent, friend, or self)
can be transformed into the mentor avatar, creating deep emotional trust.

## 2. Problem Statement

1.  Millions of students (exam pressure) and professionals (burnout) suffer silently.
2.  Therapy is expensive, stigmatized, and not accessible 24x7 in India.
3.  Existing chatbots are text-only, cold, and cannot understand emotion, voice, or disability.
4.  Deaf/mute users are completely excluded from current AI assistants.

Users don't always need a doctor. They need a mentor who listens,
guides, and helps them find clarity by themselves.

## 3. Goals & Objectives

### Primary Goal
Build an empathetic AI Mentor that provides guidance, comfort, and clarity via voice, text, sign, and avatar interaction.

### Success Metrics (For Hackathon)
- [ ] End-to-end conversation latency < 3 seconds
- [ ] Speech recognition works in English, Hindi, and Hinglish
- [ ] Facial emotion detection accuracy visible in demo
- [ ] Avatar lip-sync matches TTS audio in real-time
- [ ] Ability to create personalized avatar from 1 photo under 60 seconds
- [ ] Crisis detection triggers helpline in < 1 second

## 4. Target Users & Personas

**Persona 1: Aarav, 21, College Student**
Suffers from exam anxiety, overthinking, lack of focus. Needs a daily mentor, not a lecturer.

**Persona 2: Priya, 29, Corporate Employee**
Burnout, work-life confusion, cannot share feelings at work. Needs a private, non-judgmental companion.

**Persona 3: Rohan, 24, Deaf User**
Communicates via Indian Sign Language. Needs an AI that understands signs and replies via avatar/text.

## 5. Scope

### IN SCOPE (MVP for Hackathon)
1. Text Chat with Llama-powered Mentor
2. Real-time Speech-to-Speech Conversation
3. Facial Expression Capturing via webcam
4. Live Talking AI Avatar with Lip-Sync
5. Personalized Avatar Generation from Photo Upload
6. Basic Sign Language Recognition (10-15 signs)
7. Mood Tracking & History Dashboard
8. Crisis Detection & Helpline Support

### OUT OF SCOPE (V2 - Future)
1. Medical diagnosis or therapy prescription
2. Full ISL/ASL sentence translation
3. Group therapy / Community features
4. Payment & Subscription system
5. Native iOS/Android App (Web-first for MVP)

## 6. Functional Requirements

| ID | Feature | Description | Priority |
|---|---|---|---|
| FR-01 | User Auth | Register/Login with JWT via Spring Security | Must Have |
| FR-02 | Text Mentoring | Chat with Llama using custom Saarthi System Prompt | Must Have |
| FR-03 | Speech-to-Text | Convert user voice (Hindi/English/Hinglish) via Groq Whisper | Must Have |
| FR-04 | Text-to-Speech | Convert mentor reply to natural voice via Edge-TTS/ElevenLabs | Must Have |
| FR-05 | Speech-to-Speech | Full duplex loop: Speak -> Listen -> Understand -> Speak Back | Must Have |
| FR-06 | Emotion Capture | Detect Happy, Sad, Stressed, Tired, Neutral via MediaPipe Face Mesh in browser | Must Have |
| FR-07 | Live Avatar | Streaming avatar talks with accurate lip-sync via D-ID/MuseTalk | Must Have |
| FR-08 | Custom Avatar | User uploads 1 photo -> Generates talking mentor avatar | Must Have |
| FR-09 | Sign Language | Detect hand landmarks via MediaPipe Hands for: Hello, Help, Sad, Happy, Stress, Thank You, Yes, No, Please, I Need Help | Should Have |
| FR-10 | Emotion-Aware Reply | Mentor changes tone based on detected emotion (e.g., if user says "I'm fine" but looks sad) | Must Have |
| FR-11 | Mood Dashboard | Show user's mood history graph from `mood_logs` table | Should Have |
| FR-12 | Crisis Safety | Detect self-harm keywords -> Stop chat, show Tele-MANAS 14416 & AASRA helplines | Must Have |
| FR-13 | Language Support | Auto-detect and reply in user's language (English/Hindi/Hinglish) | Should Have |

## 7. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Performance** | Llama streaming first token < 800ms, TTS generation < 1.5s |
| **Scalability** | Java Spring Boot + WebFlux backend must handle 1000+ concurrent WebSocket sessions |
| **Privacy** | Facial video NEVER leaves browser. Only emotion label (e.g., STRESSED) sent to server. |
| **Security** | Passwords hashed with BCrypt, JWT auth, HTTPS/WSS only |
| **Availability** | System deployed with 99% uptime during demo period |
| **Accessibility** | Support for voice, text, and sign input for differently-abled users |
| **Responsible AI** | Clear disclaimer: "Not a medical therapist". No diagnosis ever. |

## 8. Technology Stack

| Layer | Technology |
|---|---|
| **Frontend** | Next.js 14, React, Tailwind CSS, MediaPipe (Face + Hands) |
| **Backend (Main)** | Java 21, Spring Boot 3.2, Spring WebFlux, WebSocket |
| **AI Service** | Python 3.11, FastAPI (for Whisper/Sign model if needed) |
| **LLM Brain** | Llama 3.3 70B / Llama 4 via Groq API |
| **Speech-to-Text** | Groq Whisper-large-v3 |
| **Text-to-Speech** | Microsoft Edge-TTS (Free) / ElevenLabs (Demo) |
| **Avatar Engine** | D-ID Streaming API (MVP) / MuseTalk (Open Source) |
| **Database** | PostgreSQL 16 |
| **Cache** | Redis |
| **Deployment** | Vercel (Frontend), Render/Railway (Java Backend), Docker |

## 9. System Flow

User Speaks/Signs -> Frontend captures Audio + Emotion + Hand Landmarks
-> WebSocket to Java Backend -> Safety Check -> Llama API -> Streamed Response
-> TTS Service -> Avatar API -> Lip-Synced Video + Audio back to User
-> Save Conversation & Mood to PostgreSQL

## 10. AI Requirements

1. Model must use the custom `Saarthi System Prompt` (see 03_MENTOR_SYSTEM_PROMPT.md)
2. Responses for voice mode must be limited to 2-3 short sentences.
3. Model must mirror user's language.
4. Model must incorporate emotion context silently without saying "I detected..."

## 11. Safety & Ethical Requirements (CRITICAL)

1. The system is a MENTOR, not a THERAPIST/DOCTOR.
2. On detection of suicide/self-harm intent, system MUST immediately display:
    - Tele-MANAS India: 14416 (24x7)
    - AASRA: 9820466726
    - iCall: 9152987821
3. No user video will be stored on the server.
4. User can delete conversation history anytime.

## 12. Assumptions & Constraints

- Assumption: User has webcam/mic and stable internet.
- Constraint: Solo developer - avatar and sign language will use pre-built APIs/models, not trained from scratch.
- Constraint: D-ID API has free credit limits - fallback is a static image with Wav2Lip animation.

## 13. Risks & Mitigation

| Risk | Impact | Mitigation |
|---|---|---|
| High latency in speech loop | Bad user experience | Stream Llama tokens sentence-by-sentence to TTS |
| Avatar API cost/limit | Demo fails | Cache avatar, keep Edge-TTS as backup, record backup demo video |
| Emotion model inaccuracy | Wrong replies | Use only as context, never as final truth. Ask user to confirm. |
| Sign language complexity | Can't build full ISL | Limit MVP to 10 static signs, perfect them. |

## 14. Acceptance Criteria (Definition of Done)

Project is considered complete for hackathon when a new user can:
1. Login, upload a photo, and generate their mentor in < 2 mins.
2. Speak "I am feeling very stressed about my exams" in Hinglish.
3. System detects STRESSED emotion from face.
4. Avatar replies empathetically in Hinglish with perfect lip-sync.
5. User shows "Help" sign, system understands it as a message.
6. After chat, mood is saved and visible on dashboard.

## 15. Milestones

- **Milestone 1:** Text Mentor with Llama (Phase 1)
- **Milestone 2:** Speech-to-Speech Mentor (Phase 2)
- **Milestone 3:** Avatar + Emotion + Sign Integration (Phase 3)
- **Milestone 4:** Deployment & Demo Video (27th Oct - 2nd Nov 2026)

---
**Approved By:** Shreynsh Singh