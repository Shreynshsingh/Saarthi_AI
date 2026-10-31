<div align="center">

# 🧭 Saarthi AI - AI Mentor

### *Your Mentor Who Sees You, Hears You, Understands You, and Talks Back - Face to Face*

[![Status](https://img.shields.io/badge/Status-In%20Development-yellow)](https://github.com/your-username/saarthi-ai)
[![Deadline](https://img.shields.io/badge/Hackathon-Meta%20Global%20AI-blue)](https://github.com/your-username/saarthi-ai)
[![Java](https://img.shields.io/badge/Backend-Java%2021%20%2B%20Spring%20Boot-orange)](https://spring.io/projects/spring-boot)
[![Llama](https://img.shields.io/badge/AI-Llama%203.3%2070B-purple)](https://www.llama.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)

**Built for Meta Global AI Developer Hackathon 2026**

[Live Demo](#) • [Demo Video](#) • [Documentation](./docs) • [Report Bug](#)

</div>

---

## 🌟 The Problem

Millions of college students, corporate professionals, and young people silently suffer from stress, anxiety, burnout, overthinking, and lack of clarity about life.

*   Therapy is expensive, inaccessible, and stigmatized.
*   Existing AI chatbots are text-only, emotion-blind, and cold.
*   Deaf and mute users are completely excluded from voice assistants.

Most people don't need a doctor. They need a **Saarthi** - a trusted guide who sits beside them, listens without judgment, and helps them find clarity themselves.

## 💡 Our Solution: Saarthi AI

Saarthi AI is not another chatbot. It's a **Real-Time, Multimodal AI Mentor** that interacts like a human on a video call.

It can:
1.  **SEE you** - Detects your facial expressions (Happy, Sad, Stressed, Tired) via your webcam.
2.  **HEAR you** - Real-time Speech-to-Speech conversation in English, Hindi, and Hinglish.
3.  **UNDERSTAND you** - Understands Sign Language for deaf/mute users.
4.  **TALK BACK to you** - Responds via a **Live Talking AI Avatar** with perfect lip-sync.

**The Magic Feature:** Upload a photo of your teacher, parent, friend, or anyone you trust, and Saarthi will turn that photo into your personal live mentor avatar.

## ✨ Key Features

| Feature | Description |
| :--- | :--- |
| 🎥 **Live AI Avatar** | Real-time talking avatar with lip-sync powered by D-ID |
| 📸 **Personalized Avatar** | Create your mentor from a single uploaded photo in <60 seconds |
| 🎤 **Speech-to-Speech** | Speak naturally, get a spoken reply back in <3 seconds |
| 😊 **Emotion Recognition** | Privacy-first facial emotion detection using MediaPipe in-browser |
| 🤟 **Sign Language Support** | Recognizes basic ISL signs like Hello, Help, Sad, Stress, Thank You |
| 🧠 **Emotion-Aware Mentoring** | Mentor adapts its tone based on your mood (e.g., you say "I'm fine" but look stressed) |
| 📊 **Mood History Dashboard** | Track your emotional journey over days/weeks |
| 🛡️ **Crisis Safety System** | Instant detection of self-harm intent and connection to helplines |
| 🌐 **Multilingual** | Full support for English, Hindi, and Hinglish conversation |

## 🎬 Demo Flow

1.  User logs in and uploads a mentor's photo.
2.  User clicks "Start Live Session" and allows camera/mic.
3.  User speaks in Hinglish: *"Main exams ko lekar bahut stressed hoon"*
4.  Saarthi detects **STRESSED** emotion from the face (87% confidence).
5.  The personalized avatar replies empathetically in Hinglish with perfect lip-sync.
6.  User shows the **HELP** sign, Saarthi understands and responds.
7.  Mood is automatically logged to the dashboard.

> **[Watch the 2-Minute Demo Video Here](#)** *(Link to be added)*

## 🏗️ System Architecture

Saarthi uses a hybrid microservice architecture for scalability and real-time performance. Java is the scalable orchestrator, Python is the AI sidecar.


graph TD
    A[User - Next.js Frontend] <-->|WebSocket| B[Java Spring Boot Backend<br/>Orchestrator]
    B --> C[Llama 3.3 70B via Groq]
    B --> D[Whisper STT via Groq]
    B --> E[Edge-TTS]
    B --> F[D-ID Live Avatar]
    B <--> G[Python FastAPI<br/>Sign Language Agent]
    B --> H[(PostgreSQL)]
    B --> I[(Redis)]
    A --> J[MediaPipe<br/>Emotion & Hand Tracking]

🛠️ Tech Stack
Layer	Technology
Frontend	Next.js 14, TypeScript, Tailwind CSS, MediaPipe JS
Backend (Core)	Java 21, Spring Boot 3.2, Spring WebFlux, WebSocket, Spring Security (JWT)
AI Service	Python 3.11, FastAPI
LLM Brain	Llama 3.3 70B Versatile via Groq API
Speech-to-Text	Groq Whisper-large-v3
Text-to-Speech	Microsoft Edge-TTS
Avatar Engine	D-ID Streaming API
Database	PostgreSQL 16, Redis 7
Deployment	Vercel (Frontend), Render (Backend), Supabase (DB)
🚀 Getting Started
Prerequisites
Java 21 & Maven
Node.js 18+ & npm
Python 3.11+
Docker Desktop
1. Clone the Repository
   bash

git clone https://github.com/your-username/saarthi-ai.git
cd saarthi-ai
2. Start Database
   bash

docker-compose up -d
# This starts PostgreSQL and Redis
3. Setup Backend (Java)
   bash

cd backend-java
cp .env.example .env # Add your GROQ_API_KEY and DID_API_KEY
./mvnw spring-boot:run
# Backend runs on http://localhost:8080
4. Setup Frontend (Next.js)
   bash

cd frontend-nextjs
npm install
npm run dev
# Frontend runs on http://localhost:3000
5. Setup AI Service (Python) - Optional for Sign Language
   bash

cd ai-service-python
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
Check backend health: http://localhost:8080/actuator/health should show {"db": "UP", "redis": "UP"}

📂 Project Structure
saarthi-ai/
├── frontend-nextjs/      # Next.js UI, MediaPipe, Avatar Player
├── backend-java/         # Spring Boot Orchestrator, WebSocket, Auth
├── ai-service-python/    # FastAPI for Sign Language classification
├── docs/                 # All project documentation
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── 05_UI_UX_DESIGN.md
│   ├── 06_DATABASE_CONNECTION.md
│   ├── 07_API_INTEGRATION.md
│   └── 08_AGENT_CONNECTION.md
└── docker-compose.yml

📖 Documentation
All detailed documents are available in the
/docs
folder:

Product Requirement Document (PRD)
System Architecture
UI/UX Design Document
API Integration Document
Agent Connection Document
Mentor System Prompt
🔒 Privacy & Safety - Responsible AI
Saarthi is built with safety as the #1 priority.

Privacy First: Your camera video is processed 100% in your browser via MediaPipe. Raw video is NEVER sent to or stored on our server. Only an emotion label like STRESSED is sent.
Not a Therapist: Saarthi is an AI Mentor for guidance and clarity. It is NOT a medical professional and never provides diagnosis.
Crisis Support: If a user expresses self-harm intent, the system immediately stops and provides verified helplines:
Tele-MANAS (India): 14416 (24x7, Toll-Free)
AASRA: 9820466726
iCall: 9152987821
🗺️ Roadmap
Phase 1: Documentation & Architecture Design
Phase 2: Text Mentoring with Llama via Spring Boot
Phase 3: Speech-to-Speech Integration
Phase 4: Live Avatar + Emotion Detection
Phase 5: Sign Language & Personalized Avatar
Phase 6: Deployment & Final Demo (Target: 2nd Nov 2026)
V2: Self-hosted MuseTalk avatar, Long-term memory with pgvector, Native Mobile App
👨‍💻 Author
Shreynsh Singh Passionate Software Engineer who loves to solve real-world problems

Solo developer for Meta Global AI Developer Hackathon.

🙏 Acknowledgements
Meta AI
for Llama models and this hackathon opportunity
Groq
for ultra-fast Llama & Whisper inference
D-ID
for Live Avatar Streaming API
Google MediaPipe
for in-browser vision
📄 License
This project is licensed under the MIT License - see the
LICENSE
file for details.
