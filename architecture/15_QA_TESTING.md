QA TESTING DOCUMENT
Project: Saarthi AI - AI Mentor Document Version: 1.0 Author / QA Engineer: Shreynsh Singh (Solo Developer) Hackathon: Meta Global AI Developer Hackathon Deadline: 2nd November 2026 Related Docs: PRD.md, 11_FEATURES_IMPLEMENTATION.md, 12_SECURITY_AND_AUTHENTICATION.md, 14_SYSTEM_INTEGRATION.md

1. Purpose
   This document defines the complete Quality Assurance strategy to verify that Saarthi AI works correctly, securely, and reliably before hackathon submission.

It covers what to test, how to test, when to test, and when the project is considered ready to submit.

QA Goal: A judge can open the live deployed link, complete the full Golden Flow without any crash, wrong emotion, security leak, or broken avatar.

2. Test Scope
   2.1 In Scope
   Area	Features Covered
   Authentication	F-01 Register, Login, Logout, JWT
   Core Mentoring	F-02 Text Chat with Llama, System Prompt behavior
   Voice	F-03 Speech-to-Text, F-04 Text-to-Speech, F-05 Speech-to-Speech
   Vision	F-06 Emotion Detection, F-07 Emotion-Aware Reply
   Avatar	F-08 Live Lip-Sync Avatar, F-09 Personalized Avatar Upload
   Accessibility	F-10 Sign Language (10 Signs)
   Dashboard	F-11 Mood Logging & History
   Safety	F-12 Crisis Detection & Helplines
   Language	F-13 English / Hindi / Hinglish
   Security	JWT Protection, CORS, File Upload, Data Ownership
   Agents & MCP	Agent Orchestration, MCP Memory Context
   Integration	Frontend <-> Java <-> Python <-> Groq <-> D-ID
   Performance	Latency < 3 seconds for speech-to-avatar
   Compatibility	Chrome Desktop, Chrome Android, Laptop & Mobile Camera
   2.2 Out of Scope
   Full ISL/ASL sentence testing - Only 10 static signs in MVP.
   Medical accuracy testing - Saarthi is a mentor, NOT a therapist.
   Load testing for 10,000+ users - Only basic concurrency test.
   iOS Safari extensive testing - Chrome is primary for hackathon demo.
   Payment testing - No payment in MVP.
3. Test Strategy & Types
   As a solo developer, follow Risk-Based Testing: Test the demo flow 80% of time, edge cases 20%.

Test Type	When	Tools
Unit Testing	While coding each module	JUnit 5, Mockito (Java), Jest (Frontend)
API Testing	After each backend API	Postman, cURL
WebSocket Testing	After WS handler is ready	wscat, Postman WS Client
Integration Testing	After each Integration Step in 14_SYSTEM_INTEGRATION.md	Manual Golden Flow
Functional / E2E Testing	Phase 3 onwards	Manual + Playwright (optional)
Security Testing	After Auth completion	Manual, Browser DevTools, Postman
Performance Testing	Final week only	Browser Network Tab, Java logs, JMeter (basic)
UAT / Demo Testing	Oct 27 - Nov 1	Test with 3-5 real friends/students
Regression Testing	After every bug fix	Re-run Golden Flow Checklist
4. Test Environment
   Component	Local Environment	Production (Test on this before submission)
   Frontend	http://localhost:3000	Vercel URL
   Java Backend	http://localhost:8080	Render URL
   Python Sign Service	http://localhost:8000	Render URL
   MCP Server	http://localhost:8001	Render Private URL
   Database	Docker PostgreSQL 16	Supabase
   Cache	Docker Redis 7	Upstash
   Browser	Chrome Latest	Chrome Desktop + Android
   Devices	Laptop with webcam	Laptop + 1 Android Phone
   Test Data Rule: Create 3 dedicated test users. Never test with production-like real personal data.

Test User	Persona	Use For
aarav@test.com / Test@123	College Student	Exam stress, Hinglish flow
priya@test.com / Test@123	Corporate Employee	Burnout, English flow
rohan@test.com / Test@123	Sign Language User	Sign + Text flow
5. Entry & Exit Criteria
   Entry Criteria (Start QA)
   Feature code is complete for the module being tested
   Backend health check shows db: UP, redis: UP
   No compilation errors in Java / Next.js / Python
   Exit Criteria (Ready to Submit)
   All Must Have test cases pass (P0 = 100%)
   No Blocker / Critical bug open
   Full Golden Flow passes on deployed URL, not just localhost
   Speech-to-Avatar latency is < 3 seconds in test logs
   Crisis flow and Security tests pass 100%
   Backup demo video recorded in case live API fails
6. Detailed Test Cases
   Priority Key: P0 = Blocker for Demo, P1 = Important, P2 = Nice to Have

6.1 Authentication (F-01)
ID	Test Case	Steps	Expected Result	Priority
TC-AUTH-01	Register with valid data	Enter name, email, 8+ char password	201, user created in DB	P0
TC-AUTH-02	Register duplicate email	Register same email again	400 "Email already exists"	P0
TC-AUTH-03	Register short password	Enter 5 char password	400 validation error	P1
TC-AUTH-04	Login with correct password	Enter valid credentials	200 + JWT returned	P0
TC-AUTH-05	Login with wrong password	Enter wrong password	401 generic "Invalid email or password"	P0
TC-AUTH-06	Access protected API without JWT	Call /mood/history no token	401 Unauthorized	P0
TC-AUTH-07	Logout and reuse token	Logout, reuse old JWT	401, token blacklisted in Redis	P1
TC-AUTH-08	Verify password hashing	Check users table in DBeaver	password_hash starts with $2a$12$	P0
6.2 Text Mentoring & Llama (F-02)
ID	Test Case	Steps	Expected Result	Priority
TC-TEXT-01	English mentoring	Type "I am stressed about exams"	Empathetic reply, 2-3 sentences	P0
TC-TEXT-02	Hinglish mentoring	Type "Main bahut stressed hoon"	Reply in Hinglish, same language	P0
TC-TEXT-03	Streaming response	Send message, watch UI	Tokens appear one-by-one, not all at once	P0
TC-TEXT-04	Not a generic chatbot	Ask personal stress question	No "As an AI..." robotic reply, asks 1 question	P1
TC-TEXT-05	Save conversation	Send 2 messages, refresh / check DB	Messages saved in messages table	P1
TC-TEXT-06	Llama latency	Send message, check Java log	First token < 800ms	P1
6.3 Voice: STT, TTS, Speech-to-Speech (F-03, F-04, F-05, F-13)
ID	Test Case	Steps	Expected Result	Priority
TC-VOICE-01	English STT	Speak "I feel anxious today"	Correct transcription shown	P0
TC-VOICE-02	Hindi STT	Speak in Hindi	Correct Hindi transcription	P0
TC-VOICE-03	Hinglish STT	Speak mixed Hinglish	Correct transcription	P0
TC-VOICE-04	TTS Playback	Get mentor reply	Clear voice plays, subtitles match audio	P0
TC-VOICE-05	Full Speech Loop	Speak -> Wait for reply	Spoken reply in < 3 seconds total	P0
TC-VOICE-06	Mic Denied	Block mic permission	Shows Text/Sign fallback, no crash	P1
TC-VOICE-07	Noisy / Unclear Audio	Speak unclearly	"I couldn't hear that clearly..." message	P1
TC-VOICE-08	Language Switching	Speak English, then Hindi	Mentor switches language automatically	P1
6.4 Emotion Detection & Emotion-Aware Reply (F-06, F-07)
ID	Test Case	Steps	Expected Result	Priority
TC-EMO-01	Live emotion badge	Smile in front of camera	Badge shows HAPPY	P0
TC-EMO-02	Sad / Low expression	Show low expression	Badge shows SAD / TIRED	P0
TC-EMO-03	Privacy check	Open Browser Network Tab during session	NO video frames sent, only emotion: STRESSED label	P0
TC-EMO-04	Camera Off Fallback	Block camera, start chat	Chat works with emotion = NEUTRAL	P0
TC-EMO-05	Emotion-Aware Reply	Say "I'm fine" with sad face	Mentor gently says "You seem a bit low today..."	P0
TC-EMO-06	No robotic phrase	Trigger TC-EMO-05	Mentor never says "I detected via camera"	P1
6.5 Avatar & Personalized Avatar (F-08, F-09)
ID	Test Case	Steps	Expected Result	Priority
TC-AVT-01	Upload valid photo	Upload JPG/PNG < 5MB, front-facing	Avatar READY in < 60 seconds	P0
TC-AVT-02	Upload invalid file	Upload .exe or 10MB file	400 rejected, safe error message	P0
TC-AVT-03	Lip-Sync Accuracy	Start session, let avatar speak	Mouth movement matches TTS audio	P0
TC-AVT-04	Personalized Avatar Appears	Upload photo, start new session	Same uploaded face appears as mentor	P0
TC-AVT-05	D-ID Fallback	Set avatar.provider=static, start session	Static photo + audio plays, no crash	P0
TC-AVT-06	Poor Photo Handling	Upload blurred / side-face photo	Clear guideline error, can re-upload	P1
6.6 Sign Language (F-10)
ID	Test Case	Steps	Expected Result	Priority
TC-SIGN-01	HELP sign	Show HELP sign to camera	Mentor receives "HELP" & responds	P1
TC-SIGN-02	Other 9 signs	Show Hello, Thank You, Sad, Happy, Stress, Yes, No, Please	Correct sign detected > 80% confidence	P1
TC-SIGN-03	Low confidence sign	Show random hand gesture	No false message sent to Llama	P1
TC-SIGN-04	Python Down Fallback	Stop Python service, use voice chat	Voice/Text still work perfectly	P0
TC-SIGN-05	Skeleton Overlay UI	Enable Sign Mode	Hand skeleton visible on camera preview	P2
6.7 Mood Dashboard (F-11)
ID	Test Case	Steps	Expected Result	Priority
TC-MOOD-01	Mood auto-logged	Complete 1 chat session	Entry created in mood_logs table	P0
TC-MOOD-02	Dashboard chart	Open Dashboard after chat	Today's mood visible on 7-day chart	P0
TC-MOOD-03	Personalized greeting	User had STRESSED mood yesterday	Dashboard shows "You felt stressed yesterday..."	P1
TC-MOOD-04	Data isolation	Login as User B, check dashboard	User A's mood NOT visible	P0
6.8 Crisis & Safety (F-12) - Most Critical Tests
ID	Test Case	Steps	Expected Result	Priority
TC-SAFE-01	Crisis via Text	Type self-harm phrase	CRISIS_ALERT in < 1 sec, Helpline modal appears	P0
TC-SAFE-02	Crisis via Voice	Speak self-harm phrase	Same crisis modal, Llama normal reply NOT shown	P0
TC-SAFE-03	Helpline Accuracy	Check crisis modal	Shows Tele-MANAS 14416 and AASRA 9820466726	P0
TC-SAFE-04	Normal stress is NOT crisis	Type "I am stressed about exams"	Normal mentoring continues, NO crisis modal	P0
TC-SAFE-05	Disclaimer visible	Open Live Session / About	"Not a medical therapist" disclaimer visible	P1
TC-SAFE-06	No Diagnosis	Ask "Do I have depression?"	Mentor refuses diagnosis, suggests professional help	P0
Ethical Testing Note: Use pre-defined test phrases only in a private test environment. Never test crisis logic with real vulnerable users.

6.9 MCP Memory Context
ID	Test Case	Steps	Expected Result	Priority
TC-MCP-01	Tool discovery	Start Java backend	Log: Tools discovered: get_recent_moods, get_user_profile, get_helpline_resources	P1
TC-MCP-02	MCP Inspector test	Call tools via MCP Inspector	Returns correct JSON from PostgreSQL	P1
TC-MCP-03	Personalized first reply	Seed 3 STRESSED moods, start session	First reply naturally references stressful week	P1
TC-MCP-04	MCP Down Fallback	Stop MCP server, start session	Session works, context empty, no crash	P0
TC-MCP-05	Security of userId	Inspect Java logs	user_id comes from JWT, never from Llama/Frontend	P1
6.10 Security Testing
Detailed checklist from 12_SECURITY_AND_AUTHENTICATION.md Section 19 must be executed fully.

ID	Test Case	Expected Result	Priority
TC-SEC-01	Tampered JWT	401, access denied	P0
TC-SEC-02	Expired JWT	Frontend redirects to /login	P0
TC-SEC-03	Access other user's conversation ID	403/404, no data leaked	P0
TC-SEC-04	Check Browser Console / Network	No Groq / D-ID API key visible	P0
TC-SEC-05	CORS test from unauthorized origin	Request blocked	P1
TC-SEC-06	WebSocket without token	Connection closed with 4401	P0
TC-SEC-07	SQL Injection attempt in login	Generic 401, no DB error leaked	P1
TC-SEC-08	Rate Limit - 10 rapid login attempts	429 after limit	P2
6.11 Integration & System Testing
Execute Full Golden Flow from 14_SYSTEM_INTEGRATION.md Section 10.2.

Additional Negative Tests:

Kill Python Service mid-session -> Core flow survives
Kill MCP Server mid-session -> Core flow survives
Disconnect internet for 5 seconds during chat -> Calm retry message, not crash
Refresh page during live session -> User can start new session cleanly
7. Non-Functional Testing
   7.1 Performance Testing
   Metric	Target	How to Measure
   Whisper STT Latency	< 500ms	Java log: [SpeechAgent] Latency
   Llama First Token	< 800ms	Java log: [MentorBrainAgent]
   TTS per Sentence	< 1.5s	Java log: [VoiceAgent]
   Total Speech-to-Avatar	< 3 seconds	Stopwatch + logs
   Page Load (Dashboard)	< 3 seconds	Chrome Lighthouse
   Avatar Generation	< 60 seconds	Stopwatch on upload
   Test with 5 concurrent users sending messages. System should not crash, latency may increase but must reply. This is sufficient for hackathon.

7.2 Compatibility Testing
Combination	Priority
Chrome + Windows Laptop (Primary Demo Device)	P0
Chrome + Android Phone	P0
Edge + Windows	P1
Firefox + Windows	P2
Check in each: Camera permission, Mic permission, WebRTC avatar playback, WebSocket connection.

7.3 Accessibility Testing
Subtitles are ON by default and readable (min 16px)
All buttons usable via keyboard / tap on mobile
Sign Mode button is visible without opening a menu
Color contrast passes - Calm Teal on Warm White is readable
Screen can be used fully in Text Mode without camera/mic
7.4 Usability Testing (UAT)
Give the deployed link to 3-5 friends (student persona) with NO instructions. Observe:

Can they understand the app in 10 seconds?
Can they start a live session in < 3 clicks?
Can they upload an avatar without help?
Do they feel the UI is calm, not overwhelming?
Record their feedback. Fix only P0 usability blockers before deadline.

8. Bug Reporting
   8.1 Severity Definitions
   Severity	Definition	Example	Fix Deadline
   Blocker	Demo completely broken	WebSocket never connects, Avatar never loads	Fix Immediately
   Critical	Core feature broken	Voice loop fails, Crisis flow fails	Same Day
   Major	Feature partially broken	1 sign fails, mood chart wrong	Within 2 Days
   Minor	UI / cosmetic	Button alignment, spelling	If time permits
   Trivial	Very small polish	Icon size	Optional
   8.2 Bug Report Template
   markdown

**Bug ID:** BUG-001
**Title:** Avatar lip-sync not working after photo upload
**Severity:** Blocker | **Priority:** P0
**Environment:** Chrome 128, Windows, Local / Deployed
**Steps to Reproduce:**
1. Login as aarav@test.com
2. Upload mentor photo
3. Start Live Session
4. Speak "Hello"
   **Expected:** Avatar speaks with lip-sync
   **Actual:** Audio plays but mouth does not move
   **Logs / Screenshot:** [Attach Java log + screenshot]
   **Status:** Open / In Progress / Fixed / Verified
   Maintain all bugs in a simple BUG_TRACKER.md or GitHub Issues. As a solo dev, do not buy Jira.

9. QA Schedule (Aligned to Deadline)
   Phase	Dates	QA Activity
   Phase 1: Foundation	Day 1-5	Unit tests + Auth API tests (TC-AUTH)
   Phase 2: Voice	Day 6-9	Voice loop tests (TC-VOICE), Latency check
   Phase 3: Avatar + Vision	Day 10-16	Emotion + Avatar tests (TC-EMO, TC-AVT), Fallback tests
   Phase 4: Polish & Safety	Day 17-21	Crisis tests, Sign tests, Security tests, MCP tests
   Regression Week	Oct 22-26	Re-run ALL P0 test cases, fix Blockers only
   UAT Week	Oct 27-30	Test with friends on DEPLOYED URL, record backup video
   Freeze & Submit	Oct 31 - Nov 2	No new features. Only Blocker fixes. Final Golden Flow test on Nov 1.
   Rule: Feature Freeze from Oct 27. If a P2 bug is found after this, document it, do not fix it and risk breaking P0 flow.

10. Regression Test Pack (Run Before Every Demo)
    This 15-minute checklist must pass before you record video or present to judges:

Register / Login works
Upload avatar works
Text chat replies in Hinglish
Voice chat replies with voice < 3s
Emotion badge updates live
Emotion-aware reply works ("I'm fine" + sad face)
Avatar lip-sync works
HELP sign works
Mood appears on dashboard
Crisis phrase shows helplines
Logout works, old token rejected
All above pass on deployed Vercel + Render URL
If any checkbox fails, you have a Blocker. Fix it before proceeding.

11. Test Deliverables
    Deliverable	Location
    This QA Testing Document	docs/15_QA_TESTING.md
    Bug Tracker	GitHub Issues / BUG_TRACKER.md
    Postman Collection	docs/postman/Saarthi.postman_collection.json
    Test Users & Data	This document Section 4
    Backup Demo Video	Recorded Oct 28, stored in Drive + Repo
    Final Test Report	One page added to README before Nov 1: "All P0 tests passed on [date]"
12. Risks in QA
    Risk	Mitigation
    Solo dev = developer tests own code, bias	Test as a user, not a developer. Use friend UAT. Follow checklists blindly.
    D-ID / Groq API fails on demo day	Fallback tests (TC-AVT-05) + backup demo video
    Emotion model varies by lighting	Test in 3 lighting conditions. For demo, ensure bright, front light.
    Sign detection accuracy low	Test same 10 signs 5 times each. Only demo the 3 most reliable signs live.
    Last-day regression breaks core flow	Code freeze Oct 27, Regression Pack before every change
13. Definition of Done - QA Complete
    QA is complete and project is ready to submit when:

100% of P0 test cases pass
0 Blocker and 0 Critical bugs open
Regression Pack passes on both localhost and deployed URL
Security and Crisis checklists pass 100%
3+ real users completed Golden Flow in UAT without help
Backup 2-minute demo video is recorded and verified