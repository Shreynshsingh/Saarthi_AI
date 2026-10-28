# Saarthi AI Backend (Java)

Spring Boot 3.3.5 / Java 21. Modules 1–3: JWT auth, Llama text mentoring,
Whisper STT + Edge-TTS speech-to-speech. Module 5: mentor photo upload + D-ID
lip-synced avatar with static fallback.

## Run

```bash
docker-compose up -d            # from repo root: Postgres + Redis
cd backend-java
cp .env.example .env            # then set GROQ_API_KEY (and DID_API_KEY for live avatar)
set -a; source .env; set +a
./mvnw spring-boot:run           # serves :8080, uploads at /uploads/**
```

## Avatar uploads (Module 5)

- `POST /api/v1/mentor/upload-avatar` (JWT, multipart `file` + `mentorName` +
  optional `voiceId`) validates JPG/PNG/WebP by magic bytes, enforces 5MB, and
  stores photos at `uploads/avatars/{userId}/{UUID}.{ext}`.
- `GET /api/v1/mentor/my-mentor` returns only the caller's own profile.
- `POST /api/v1/mentor/avatar-stream` (+ `/sdp`, `/ice`, `DELETE`) proxies D-ID
  signaling so the key never reaches the browser.
- Kill switch: `AVATAR_PROVIDER=static` (or blank `DID_API_KEY`) disables live
  video; sessions run photo + voice with zero D-ID calls.

NOTE: D-ID cannot fetch localhost URLs. For local avatar generation tests, if
D-ID cannot reach the image the profile is kept with status FALLBACK_STATIC
(STATIC_READY) and the photo still serves static mode. A deployed backend URL
will work.
