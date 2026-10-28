UI/UX DESIGN DOCUMENT
Project: Saarthi AI - AI Mentor Version: 1.0 Designer & Developer: Shreynsh Singh Platform: Web App (Desktop First, Mobile Responsive) Tool for Design: Figma Tool for Development: Next.js 14 + Tailwind CSS

1. Design Philosophy
   Saarthi is not a chatbot. It's a safe space.

Our design must feel like sitting with a calm mentor in a peaceful room - not like using a corporate AI tool.

Core Principles
Calm Over Clever: No harsh colors, no popups, no clutter. Every screen should reduce anxiety, not increase it.
Human Over Robotic: Use rounded corners, soft shadows, warm language, and a living avatar as the hero.
Privacy By Design: Always show the user "Camera is ON, but video is NOT recorded" to build trust.
Accessible For All: Design for 3 inputs equally - Voice, Text, and Sign Language. A deaf user should not feel like a second-class user.
One Focus Per Screen: In the live session, the user should only focus on the Mentor. Everything else fades away.
2. Brand Identity
   Name: Saarthi AI Meaning: Saarthi = The Guide / Charioteer who sits beside you Tagline: Your Mentor Who Sees, Hears, and Understands You. Brand Personality: Empathetic Elder Sibling, Wise Teacher, Non-Judgmental Friend Logo Concept: A simple circle (wholeness) with a subtle path/light inside. Use in Figma as a placeholder.

Voice & Tone (Microcopy):

Never write: "Error: Input not recognized"
Write instead: "I couldn't hear that clearly. Take your time, I'm here."
Never write: "Ask me anything"
Write instead: "What's on your mind today?"
3. Color Palette
   Avoid red/orange/pure black. They increase anxiety.

Role	Color Name	Hex Code	Usage
Primary	Calm Teal	#2A9D8F	Primary buttons, Logo, Active states
Secondary	Soft Sage	#A7C4BC	Background gradients, Cards
Background	Warm White	#F7F9F9	Main app background (not pure white)
Dark Surface	Deep Slate	#1E2A32	Live Session background (focus mode)
Accent	Warm Amber	#E9C46A	Highlights, Mood chart, Gentle alerts
Text Primary	Charcoal Soft	#2D3748	Headings, Body text
Text Secondary	Grey Mist	#718096	Subtitles, placeholders
Success / Happy	Soft Green	#81C784	Happy mood indicator
Sad / Low	Muted Blue	#64B5F6	Sad mood indicator (never use dark red)
Crisis Alert	Soft Coral	#E76F51	ONLY for SOS / Helpline button
Dark Mode: Live Mentor Session will be dark by default to make the Avatar feel like a real video call and reduce eye strain.

4. Typography
   Element	Font	Size	Weight
   Headings	Poppins / Plus Jakarta Sans	28-36px	600 SemiBold
   Body	Inter	16px	400 Regular
   Mentor Speech	Inter	18px	400 Regular (larger for readability)
   Caption / Hint	Inter	13px	400, Grey Mist
   Why Inter + Poppins: Clean, friendly, excellent Hindi/Devanagari support for Hinglish users.

5. Information Architecture (Sitemap)
   mermaid

graph TD
A[Landing Page] --> B[Login / Signup]
B --> C[Onboarding - 3 Steps]
C --> C1[1. Your Name & Language]
C --> C2[2. Choose Your Mentor]
C --> C3[3. Upload Photo for Avatar]
C --> D[Dashboard / Home]
D --> E[Live Mentor Session - CORE SCREEN]
D --> F[Mood History]
D --> G[My Mentor Profile]
D --> H[Settings & Privacy]
E --> E1[Text Mode]
E --> E2[Voice Mode]
E --> E3[Sign Language Mode]
6. User Flows
   Flow 1: First-Time User (Golden Flow)
   Landing -> Sign Up -> Onboarding -> Upload Mentor Photo -> Click 'Talk to Saarthi' -> Allow Camera/Mic -> Start Talking

Target time: Under 2 minutes.

Flow 2: Returning User (Daily Check-in)
Login -> Dashboard shows "You felt stressed yesterday. Want to talk today?" -> 1-Click Start Session -> Mentor greets by name & last mood

Flow 3: Crisis Flow
User says/types self-harm phrase -> Session PAUSES immediately -> Calm overlay appears -> Show Tele-MANAS 14416 button -> Show "Call a Trusted Person" -> Button: "I am safe, continue talking"

7. Screen-by-Screen Design
   7.1 Landing Page
   Goal: Emotional trust in 5 seconds.

Hero: Large headline "Feeling lost? Talk to a mentor who truly listens."
Subtext: "Saarthi sees your expressions, hears your voice, understands your signs, and talks back - face to face."
CTA Button [Start Talking - It's Free]
Visual: Demo video/GIF of the Avatar talking (right side)
Sections Below: How it works (3 cards), For Students/Professionals/Deaf Users, Safety & Privacy, Footer
7.2 Dashboard / Home
Clean, not crowded.

Top: "Good Evening, Shreynsh" + Profile Avatar
Center Card: Today's Mood Check-in: [😊 Happy] [😐 Neutral] [😔 Low] [😰 Stressed]
Big Hero Card: Your Mentor Avatar preview + Button [ 🎥 Start Live Session ]
Bottom: Weekly Mood Graph (simple line chart) + Recent Conversations list
7.3 CORE SCREEN: Live Mentor Session
This is 90% of your demo. Design it like a Google Meet / FaceTime call, not a chat app.

+-------------------------------------------------------------+
| Saarthi ● LIVE                          [Emotion: Stressed] | <- Top Bar (Privacy indicator)
|                                                             |
|                                                             |
|                 [  AI AVATAR VIDEO  ]                       |
|                 Large, Center, Rounded                      |
|                 Lip-syncing, Blinking                       |
|                                                             |
|  Subtitle: "Samajh sakta hoon, exams ka pressure..."        | <- Live Captions
|                                                             |
| [Your Camera Preview]                                       | <- Small PiP bottom-left
|                                                             |
|   [🎤 Mic] [⌨️ Text] [🤟 Sign] [📞 End]                     | <- Control Bar
+-------------------------------------------------------------+

States for this screen:

Listening State: Mic button pulses in Calm Teal, text "I'm listening..."
Thinking State: Avatar looks thoughtful, subtle 3-dot animation "Saarthi is thinking..."
Speaking State: Waveform animation, subtitles appear word-by-word, mic auto-muted.
Sign Mode State: Hand skeleton overlay appears on your camera preview, detected sign shows as text e.g., [Sign Detected: HELP]
Right Side Panel (Desktop Only, Collapsible): Live text transcript of conversation.

7.4 Create Your Mentor Screen
Left: Upload box [Upload a photo of your teacher, parent, or anyone you trust]
Guideline: "Use a clear, front-facing photo with good light"
Right: Preview - after generation: [Your Mentor is Ready!] with a test "Hello" audio.
Options: Choose Mentor Name, Choose Voice (Male/Female, Hindi/English)
7.5 Mood History Screen
Simple Line/Bar chart of last 7/30 days from mood_logs
Insight Card generated by Llama: "You felt most stressed on weekdays. Talking on Tuesday helped your mood improve."
No complex medical graphs.
7.6 Crisis / SOS Modal
Design must be calm, NOT alarming red.

Title: "I'm really glad you told me this."
Text: "You don't have to go through this alone. Please talk to someone who can help right now."
Big Button 1: [ Call Tele-MANAS: 14416 ]
Big Button 2: [ Call AASRA: 9820466726 ]
Link: "Message a trusted contact"

8. Component Library (Build in Tailwind)
   Component	Style Specification
   Primary Button	bg-[#2A9D8F], rounded-full, px-6 py-3, text-white, soft shadow
   Card	bg-white, rounded-3xl (24px), shadow-sm, p-6, border border-slate-100
   Avatar Container	rounded-3xl, aspect-video, ring-2 ring-teal-500/20 when speaking
   Input Field	rounded-full, bg-slate-100, focus:ring-2 ring-teal-400
   Emotion Badge	pill-shape, dynamic color, e.g., bg-blue-100 text-blue-700 "Feeling: Low"
   Mic Button	w-16 h-16, rounded-full, animate-pulse when listening
   Design Tokens for Tailwind (tailwind.config.js): Add colors: primary: '#2A9D8F', sage: '#A7C4BC', calm: '#F7F9F9', slateDark: '#1E2A32'

9. Responsiveness
   Desktop (1280px+): Avatar center, transcript side panel visible, PiP camera bottom-left.
   Tablet (768px): Transcript becomes bottom sheet.
   Mobile (375px): Full-screen Avatar like a video call, controls at bottom thumb-reach, text input expands above keyboard. This is critical for students.
10. Accessibility (WCAG AA)
    All buttons have labels for screen readers.
    Subtitles are ON by default for every avatar speech (for deaf/hard-of-hearing users).
    Sign Mode button is always visible, not hidden in a menu.
    Minimum text contrast ratio 4.5:1.
    Captions font size minimum 16px.
11. Motion & Animation
    Keep it slow and soft. No fast/bouncy animations.

Page transition: Fade-in 300ms
Avatar speaking ring: Gentle pulse 1.5s
Mic listening: Soft breathing pulse
Use Framer Motion in Next.js for these.
12. Empty, Loading & Error States
    No Conversations: Illustration + "Your first conversation is waiting. Saarthi is here when you're ready."
    Avatar Generating: Progress steps: "Uploading photo... Creating your mentor... Adding voice... Almost ready" (takes focus away from wait time)
    Mic Denied: "We need mic access to hear you. You can still use Text or Sign mode." [Use Text Mode button]
13. Figma File Structure (Create These Pages)
    00_Cover
    01_Design_System (Colors, Fonts, Buttons)
    02_Wireframes_LoFi (Black & white boxes)
    03_UI_HiFi_Desktop
    04_UI_HiFi_Mobile
    05_Prototype_Flow (Connect Golden Flow)
    06_Assets (Icons, Illustrations)
    Deadline Tip for Solo Dev: Don't design 20 screens in Figma. Design only 4 perfectly: Landing, Dashboard, Live Session, Create Mentor. Build the rest directly in code.

14. UX Success Criteria
    The design is successful if a first-time user in the demo can:

Understand what Saarthi is in <10 seconds on Landing Page.
Start a live talking session in <3 clicks from Dashboard.
Switch from Voice to Sign to Text without leaving the session.
Clearly see their detected emotion and trust that video is private.