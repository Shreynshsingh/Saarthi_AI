AGENT ORCHESTRATION & CONNECTION DOCUMENT
Project: Saarthi AI - AI Mentor Version: 1.0 Author: Shreynsh Singh (Solo Developer) Orchestrator: Java 21 + Spring Boot 3.2 Deadline: 2nd November 2026

1. Why Agents Are Required In Saarthi?
   A normal chatbot has 1 brain. Saarthi has multiple senses:

User can speak, show emotion on face, and show a sign at the same time. One single API cannot process all of this.

So we split the system into specialized Agents. Each agent does ONE job perfectly, and Java connects them in the right order.

Without this document: You will write messy, tangled code in WebSocket handler. With this document: Each agent is independent, testable, and replaceable.

2. Agent Architecture Overview
   We do NOT use a heavy framework like LangChain/AutoGen. For real-time voice (<3 sec latency), we use a lightweight Java Orchestrator Pattern.

mermaid

graph TD
User([User]) --> FE[Next.js Frontend]
​
subgraph Perception["PERCEPTION AGENTS - Input"]
A1[Emotion Agent<br/>MediaPipe Face]
A2[Speech Agent<br/>Whisper STT]
A3[Sign Agent<br/>MediaPipe Hands + Python]
end
​
subgraph Core["CORE AGENTS - Thinking"]
Orchestrator[MENTOR ORCHESTRATOR<br/>Java Spring Boot]
A4[Safety Agent<br/>Crisis Detection]
A5[Mentor Brain Agent<br/>Llama 3.3 70B]
A6[Memory Agent<br/>Mood + History]
end
​
subgraph Action["ACTION AGENTS - Output"]
A7[Voice Agent<br/>Edge-TTS]
A8[Avatar Agent<br/>D-ID / MuseTalk]
end
​
FE --> A1
FE --> A2
FE --> A3
A1 --> Orchestrator
A2 --> Orchestrator
A3 --> Orchestrator
Orchestrator --> A4
A4 -->|Safe| A5
A4 -->|Crisis| FE
A6 <--> A5
A5 --> A7
A7 --> A8
A8 --> FE
Java Backend = Orchestrator / Traffic Police Frontend and Python never talk to Llama directly. Everything goes through the Orchestrator.

3. Agent Definitions & Contracts
   Every agent MUST follow a fixed Input -> Output contract. This is how they connect.

Agent	Location	Input	Output	Tech
1. Emotion Agent	Browser	Webcam Frames	{emotion: STRESSED, confidence: 0.87}	MediaPipe Face Mesh
2. Speech Agent	Java -> Groq	Audio Bytes	{text: "I am stressed"}	Whisper-large-v3
3. Sign Agent	Python FastAPI	Hand Landmarks [[x,y,z]...]	{sign: HELP, confidence: 0.94}	MediaPipe + LSTM
4. Safety Agent	Java	User Text	{isCrisis: false} OR Crisis Alert	Keyword + Rule Engine
5. Mentor Brain Agent	Java -> Groq	Text + Emotion + Memory	Streamed Text Sentences	Llama 3.3 70B
6. Memory Agent	Java + PostgreSQL	User ID	Last 3 Moods + Saves New Mood	Spring JPA + Redis
7. Voice Agent	Java	Text Sentence	Audio MP3 Bytes	Edge-TTS
8. Avatar Agent	Java -> D-ID	Audio + Photo ID	WebRTC Video Stream	D-ID Streaming API
4. Connection Protocols
   4.1 How Agents Connect
   Connection	Protocol	Reason
   Frontend <-> Java Orchestrator	WebSocket (WSS)	Real-time, bidirectional, low latency
   Java <-> Python Sign Agent	REST (Internal HTTP)	Simple request/response for landmarks
   Java <-> Llama / Whisper	HTTPS REST + Streaming	Groq API is OpenAI-compatible
   Java <-> Avatar Agent	HTTPS + WebRTC	D-ID returns a video stream for Frontend
   Java <-> Memory Agent	JPA + Redis	Direct DB access, no network delay
   4.2 Universal Agent Message Format
   All agents send data to the Orchestrator in this common JSON format. Create this DTO in Java:

java

// dto/AgentContext.java
public record AgentContext(
UUID conversationId,
UUID userId,
String textInput,        // From Speech or Text Agent
String signInput,        // From Sign Agent: "HELP"
String emotion,          // From Emotion Agent: "STRESSED"
Double emotionConfidence,
String inputMode         // TEXT / VOICE / SIGN
) {}
This one object is passed from agent to agent. No agent needs to know about the others.

5. Core Orchestration Flow
   This is the sequence your MentorWebSocketHandler must execute. Order is critical.

mermaid

sequenceDiagram
participant FE as Frontend
participant ORC as Orchestrator (Java)
participant SAFE as Safety Agent
participant MEM as Memory Agent
participant LLAMA as Mentor Brain
participant VOICE as Voice+Avatar
​
FE->>ORC: AgentContext {audio, emotion: STRESSED}
ORC->>ORC: Speech Agent: Audio -> Text
ORC->>SAFE: Check Text
alt Is Crisis
SAFE-->>FE: CRISIS_ALERT (Stop all agents)
else Is Safe
ORC->>MEM: Get last 3 moods
MEM-->>ORC: [STRESSED, TIRED, NEUTRAL]
ORC->>LLAMA: Text + Emotion + Memory + System Prompt
LLAMA-->>ORC: Stream Sentence 1
ORC->>VOICE: Sentence 1 -> Audio -> Avatar Speak
VOICE-->>FE: Play Avatar Video
ORC->>MEM: Save Message + New MoodLog
end
Execution Rules:
Sequential Gate: Safety Agent runs BEFORE Llama. Always.
Parallel Perception: Emotion, Speech, and Sign agents run in parallel in the frontend. The Orchestrator just collects their final result.
Streaming Action: Voice + Avatar agents don't wait for Llama's full answer. They process 1 sentence at a time.
6. Java Implementation Structure
   Create a dedicated agent package. Do not put all logic in the WebSocket Handler.

src/main/java/com/saarthi/ai/
└── agent/
├── orchestrator/
│   └── MentorOrchestrator.java  <-- ONLY this class connects all agents
├── perception/
│   ├── SpeechAgent.java         (calls Whisper)
│   └── SignAgentClient.java     (calls Python FastAPI)
├── core/
│   ├── SafetyAgent.java
│   ├── MentorBrainAgent.java    (calls Llama)
│   └── MemoryAgent.java         (calls DB/Redis)
└── action/
├── VoiceAgent.java          (calls TTS)
└── AvatarAgent.java         (Interface for D-ID)

Mentor Orchestrator Code (Core Logic)
java

@Service
@RequiredArgsConstructor
public class MentorOrchestrator {
​
private final SpeechAgent speechAgent;
private final SafetyAgent safetyAgent;
private final MemoryAgent memoryAgent;
private final MentorBrainAgent brainAgent;
private final VoiceAgent voiceAgent;
​
public Flux<String> processUserMessage(AgentContext context, String audioBase64) {
// 1. Resolve Input: If VOICE, convert to text. If SIGN, use sign text.
String userText = resolveText(context, audioBase64);
​
// 2. SAFETY GATE
if (safetyAgent.isCrisis(userText)) {
return Flux.just("CRISIS_ALERT"); // Handler will intercept this
}
​
// 3. MEMORY: Get context
var moodHistory = memoryAgent.getRecentMoods(context.userId());
​
// 4. BRAIN: Stream Llama response
return brainAgent.streamResponse(userText, context.emotion(), moodHistory)
.doOnNext(sentence -> {
// 5 & 6. ACTION: This will be handled by WebSocketHandler for TTS/Avatar streaming
memoryAgent.saveMessage(context.conversationId(), sentence);
});
}
​
private String resolveText(AgentContext ctx, String audio) {
if ("SIGN".equals(ctx.inputMode())) return ctx.signInput();
if ("VOICE".equals(ctx.inputMode())) return speechAgent.transcribe(audio);
return ctx.textInput();
}
}
Your MentorWebSocketHandler now becomes very clean — it only calls mentorOrchestrator.processUserMessage().

7. Agent Failure & Fallback Strategy
   In a hackathon demo, one API failing should NOT kill the whole system.

Agent Fails	Fallback Behavior
Emotion Agent fails / Camera off	Orchestrator proceeds with emotion = NEUTRAL. User can still talk.
Speech Agent fails	Send error to Frontend: "I couldn't hear that. Please try Text mode."
Sign Agent (Python down)	Ignore sign input. Voice/Text continue to work. Log warning.
Llama Agent times out (>10s)	Voice Agent speaks fallback: "I'm here with you. Let's take a deep breath together."
Avatar Agent (D-ID credits over)	Show static mentor photo + play only Voice Agent audio. Still a working mentor.
Memory Agent (DB slow)	Skip history, proceed with fresh conversation using Redis cache.
Implement this with a simple try-catch / Resilience timeout per agent, not globally.

8. Configuration
   All agent connections are configured in application.yml. Never hardcode URLs.

yaml

saarthi:
agents:
python-sign-service:
url: http://localhost:8000/predict-sign
timeout-ms: 2000
groq:
base-url: https://api.groq.com/openai/v1
llama-model: llama-3.3-70b-versatile
whisper-model: whisper-large-v3
avatar:
provider: did # Options: did, musetalk, static
api-url: https://api.d-id.com
This allows you to switch Avatar Agent from did to static with 1 line change during demo if needed.

9. Testing Agent Connections
   Test agents in isolation BEFORE connecting them all.

Unit Test: Emotion Agent -> Send test image -> Expect STRESSED label
Unit Test: Speech Agent -> Send Hindi audio file -> Expect correct text
Unit Test: Safety Agent -> Send "I want to die" -> Expect isCrisis = true
Integration Test: Send full AgentContext via WebSocket -> Receive Avatar stream
Failure Test: Turn off Python service -> Voice chat still works
10. Do's and Don'ts for Solo Developer
    DO:

Build agents in this order: Brain -> Speech -> Voice -> Safety -> Emotion -> Avatar -> Sign (last, it's hardest).
Log every agent step: [Agent: Brain] Latency: 450ms — this will save you during debugging.
Keep Python Sign Agent completely separate. If it crashes, your Java app must survive.
DON'T:

Don't let Frontend call Python or Groq directly. It breaks security and orchestration.
Don't make agents call each other. ONLY the Orchestrator calls agents.
Don't try to build a multi-agent chat where Llama talks to another Llama. That's slow, costly, and not needed here.
