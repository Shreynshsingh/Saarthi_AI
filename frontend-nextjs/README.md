# Saarthi AI Frontend (Module 4)

Next.js 14 live mentor session: auth, dashboard, video-call style session with
text + voice + sign input, browser-side emotion detection, ordered voice
playback, live subtitles, and a calm crisis modal.

Backend modules 1–3 must be running (Java on `:8080`, Python TTS on `:8000`).

## Equivalent create-next-app command

This folder was authored directly. The equivalent scaffold command is:

```bash
npx create-next-app@14 frontend-nextjs --typescript --tailwind --app --no-src-dir --import-alias "@/*"
```

## Run

```bash
cd frontend-nextjs
cp .env.local.example .env.local
npm install
npm run dev
```

Open http://localhost:3000 in a browser. Camera and microphone need a
localhost or HTTPS context; both degrade gracefully when blocked.

Module 5: create a personal mentor at /mentor/create. Live lip-sync needs a
D-ID key in the backend plus a publicly reachable photo URL (tunnel or deploy);
otherwise sessions automatically use the mentor photo + voice.
