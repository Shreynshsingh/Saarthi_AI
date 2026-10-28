API INTEGRATION DOCUMENT
Project: Saarthi AI - AI Mentor
Version: 1.0
Author: Shreynsh Singh (Solo Developer)
Backend: Java 21 + Spring Boot 3.2
Frontend: Next.js 14
Deadline: 2nd November 2026

1. Overview
   Saarthi AI has 3 types of APIs:

Type	Used Between	Protocol
Internal REST API	Next.js <-> Java Backend	HTTPS / JSON
Realtime API	Next.js <-> Java Backend	WSS / WebSocket JSON
External AI APIs	Java Backend <-> Groq / D-ID / TTS	HTTPS / REST
Golden Rule: Frontend NEVER calls Llama, Whisper, or D-ID directly. All AI keys stay secret in Java Backend. Frontend only talks to Java.

Frontend -> Java Backend -> [ Llama + Whisper + TTS + D-ID + Python ]

2. Base URLs & Environments
   Environment	Frontend	Java Backend	Python AI Service
   Local	http://localhost:3000	http://localhost:8080	http://localhost:8000
   Production	https://saarthi.vercel.app	https://saarthi-api.onrender.com	https://saarthi-ai.onrender.com
   REST Base: /api/v1 WebSocket Base: /ws/mentor

3. Authentication
   All protected APIs require JWT.

Header:

Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

Token is obtained from Login API and stored in frontend as localStorage / secure cookie. Token expiry: 24 hours for hackathon.

4. Environment Variables (.env for Backend)
   Never expose these in Frontend or GitHub.

env

# Groq (Llama + Whisper)
GROQ_API_KEY=gsk_xxxx
​
# D-ID Avatar
DID_API_KEY=xxxx
DID_BASE_URL=https://api.d-id.com
​
# App
JWT_SECRET=your_super_secret_key_min_32_chars
FRONTEND_URL=http://localhost:3000
5. Internal REST APIs (Frontend <-> Java)
   5.1 Auth APIs
   POST /api/v1/auth/register
   Request:

json

{
"name": "Shreynsh Singh",
"email": "shreynsh@example.com",
"password": "password123",
"preferredLanguage": "en-IN"
}
Response 201:

json

{
"success": true,
"message": "User registered successfully",
"data": { "id": "uuid", "name": "Shreynsh Singh", "email": "shreynsh@example.com" }
}
POST /api/v1/auth/login
Request: { "email": "...", "password": "..." } Response 200:

json

{
"success": true,
"data": { "token": "eyJhbGciOi...", "userId": "uuid", "name": "Shreynsh" }
}
5.2 Mentor / Avatar APIs
POST /api/v1/mentor/upload-avatar
Upload photo to create personalized avatar. Content-Type: multipart/form-data

Form Data: file: image.jpg, mentorName: "My Teacher", voiceId: "en-IN-female"

Java Logic: Upload file -> Call D-ID API -> Save avatar_provider_id in mentor_profiles table.

Response 200:

json

{
"success": true,
"data": {
"mentorProfileId": "uuid",
"mentorName": "My Teacher",
"avatarImageUrl": "https://.../image.jpg",
"avatarProviderId": "did_presenter_id_123",
"status": "READY"
}
}
GET /api/v1/mentor/my-mentor
Returns current user's mentor profile.

5.3 Conversation & Mood APIs
GET /api/v1/conversations
List past conversations for logged-in user.

GET /api/v1/mood/history?days=7
Response:

json

{
"success": true,
"data": [
{ "mood": "STRESSED", "confidence": 0.87, "loggedAt": "2026-10-20T18:30:00" },
{ "mood": "NEUTRAL", "confidence": 0.65, "loggedAt": "2026-10-21T09:15:00" }
]
}
GET /actuator/health
Public health check. Must return "db": "UP", "redis": "UP"

6. Realtime WebSocket API (Core Feature)
   This is the most important API. One connection = One live mentor session.

Connect: ws://localhost:8080/ws/mentor?token=<JWT_TOKEN>

Client -> Server Message
Frontend sends this when user speaks / types / signs:

json

{
"type": "USER_MESSAGE",
"conversationId": "uuid",
"inputMode": "VOICE",
"text": "I am feeling stressed about my exams",
"audioBase64": "UklGRiQAAABXQVZF...",
"emotion": "STRESSED",
"emotionConfidence": 0.87,
"signDetected": null
}
For Sign Mode: "inputMode": "SIGN", "signDetected": "HELP", "text": null

Server -> Client Messages (Streamed)
1. Transcription (if voice):

json

{ "type": "TRANSCRIPTION", "text": "I am feeling stressed about my exams" }
2. Mentor Chunk (Streamed sentence by sentence):

json

{
"type": "MENTOR_RESPONSE_CHUNK",
"textChunk": "Samajh sakta hoon, exam ka pressure bahut heavy lagta hai.",
"audioBase64": "...",
"isFinal": false
}
3. Final:

json

{ "type": "MENTOR_RESPONSE_END", "conversationId": "uuid", "detectedMood": "STRESSED" }
4. Crisis (Bypasses Llama):

json

{
"type": "CRISIS_ALERT",
"message": "I'm really glad you told me this. Please call Tele-MANAS at 14416.",
"helplines": ["14416", "9820466726"]
}
5. Error:

json

{ "type": "ERROR", "message": "I couldn't hear that clearly. Take your time, I'm here." }
Frontend Integration Code (Next.js)
typescript

const ws = new WebSocket(`ws://localhost:8080/ws/mentor?token=${token}`);
​
ws.onopen = () => console.log("Connected to Saarthi");
​
ws.onmessage = (event) => {
const data = JSON.parse(event.data);
if (data.type === "MENTOR_RESPONSE_CHUNK") {
setMentorText(prev => prev + data.textChunk);
playAudio(data.audioBase64); // Avatar lip-sync triggered here
}
if (data.type === "CRISIS_ALERT") setShowCrisisModal(true);
};
​
// Send user voice/text
ws.send(JSON.stringify({
type: "USER_MESSAGE",
conversationId,
inputMode: "VOICE",
text: userText,
emotion: detectedEmotion, // from MediaPipe: "STRESSED"
emotionConfidence: 0.87
}));
7. External API Integrations (Java <-> AI Providers)
   7.1 Llama 3.3 via Groq (The Brain)
   Endpoint: POST https://api.groq.com/openai/v1/chat/completions Used in: LlamaService.java Headers: Authorization: Bearer ${GROQ_API_KEY}

Java WebClient Request:

java

Map<String, Object> body = Map.of(
"model", "llama-3.3-70b-versatile",
"stream", true,
"temperature", 0.7,
"messages", List.of(
Map.of("role", "system", "content", SYSTEM_PROMPT), // From 03_MENTOR_SYSTEM_PROMPT.md
Map.of("role", "user", "content", "Emotion: " + emotion + "\nUser: " + userText)
)
);
return webClient.post().uri("/chat/completions")
.header("Authorization", "Bearer " + groqApiKey)
.bodyValue(body).retrieve().bodyToFlux(String.class);
Rule: Always stream. Never wait for full response.

7.2 Whisper via Groq (Speech-to-Text)
Endpoint: POST https://api.groq.com/openai/v1/audio/transcriptions Model: whisper-large-v3 Input: Audio file (webm/mp3) from frontend Output: { "text": "I am stressed" } Supports Hindi, English, Hinglish automatically.

7.3 Text-to-Speech (Edge-TTS)
Used in: TtsService.java Flow: Llama sentence -> Edge-TTS -> MP3 audio bytes -> Send to Frontend + D-ID Voices: en-IN-NeerjaNeural (Female), en-IN-PrabhatNeural (Male), hi-IN-SwaraNeural (Hindi)

7.4 D-ID Avatar (Lip-Sync)
1. Create Stream: POST https://api.d-id.com/talks/streams Body: { "source_url": "user_uploaded_photo_url" } -> Returns stream_id, session_id

2. Send Talk / SDP: Send TTS audio URL + stream_id to make avatar speak.

3. Frontend: Receives WebRTC video stream from D-ID and displays it in the Avatar box.

Fallback: If D-ID fails/credits end, AvatarService returns static image + audio only. Frontend must handle this gracefully.

Architecture Note: Create AvatarService as an interface. DidAvatarServiceImpl is MVP, MuseTalkServiceImpl will be V2. This way you never rewrite WebSocket code.

7.5 Python AI Service (Sign Language)
Java calls Python only for sign classification. Python never calls Java.

Endpoint: POST http://localhost:8000/predict-sign Request from Java:

json

{ "landmarks": [[0.51, 0.32, 0.01], [0.53, 0.35, 0.02]] }
Response from Python:

json

{ "sign": "HELP", "confidence": 0.94 }
Java Logic: If confidence > 0.80, treat sign as user text and send to Llama.

8. Standard Response & Error Format
   All REST APIs follow this format:

Success: { "success": true, "message": "...", "data": { ... } } Error: { "success": false, "message": "Email already exists", "errorCode": "USER_EXISTS" }

HTTP Code	Meaning	Frontend Action
200 / 201	Success	Continue
400	Bad Request	Show validation message
401	Unauthorized	Redirect to Login
429	Rate Limit (Redis)	Show "Please wait a moment"
500	Server / AI API Error	Show calm retry message, never show stack trace
9. Integration Flow Summary
1. User Login -> Frontend saves JWT
2. User uploads photo -> POST /mentor/upload-avatar -> Java calls D-ID -> Saves DB
3. User clicks Start -> WebSocket connects with JWT
4. User speaks -> Frontend sends audio + emotion (MediaPipe)
5. Java: Crisis Check -> Whisper -> Llama (stream) -> TTS -> D-ID
6. Frontend plays avatar video chunk by chunk
7. Java saves Message + MoodLog to PostgreSQL
8. User opens Dashboard -> GET /mood/history -> Chart renders

10. Testing APIs
    With cURL:

bash

curl -X POST http://localhost:8080/api/v1/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"shreynsh@example.com","password":"password123"}'
Testing Checklist:

Use Postman Collection: Auth -> Upload Avatar -> Get Mentor -> Mood History
Use wscat -c ws://localhost:8080/ws/mentor?token=... to test WebSocket
Test with wrong JWT -> Must get 401
Test crisis phrase "I want to die" -> Must get CRISIS_ALERT, not Llama reply
Test Hindi voice -> Whisper must transcribe correctly
11. Rate Limiting & Latency Rules
    Rate limit via Redis: Max 20 messages / minute per user.
    Target latency: Whisper 400ms + Llama first token 500ms + TTS 600ms = < 2.5 sec total
    If Groq/D-ID times out after 10s, send fallback text response to frontend. Never leave user hanging.
