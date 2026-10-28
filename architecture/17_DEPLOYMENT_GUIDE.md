DEPLOYMENT GUIDE DOCUMENT
Project: Saarthi AI - AI Mentor Document Version: 1.0 Author: Shreynsh Singh (Solo Developer) Hackathon: Meta Global AI Developer Hackathon Deadline: 2nd November 2026 Related Docs: 10_SETUP_AND_INITIALIZATION.md, 14_SYSTEM_INTEGRATION.md, 15_QA_TESTING.md, 16_ERRORS_AND_DEBUGGING.md

1. Purpose
   This document defines how to deploy Saarthi AI to production so anyone with a link can use it without installing anything.

Deployed System Goal:

User opens Vercel Link -> Logs in -> Talks to Avatar -> Mood Saved

No localhost, no manual setup for judges.

2. Deployment Architecture
   mermaid

graph TD
User([Judge / User]) -->|HTTPS| Vercel[Vercel<br/>Next.js Frontend]
Vercel -->|HTTPS + WSS| RenderJava[Render<br/>Java Spring Boot Backend]
RenderJava -->|Internal HTTP| RenderPy[Render<br/>Python Sign Service]
RenderJava -->|Internal HTTP / MCP| RenderMCP[Render Private Service<br/>MCP Server]
RenderJava -->|JDBC| Supabase[Supabase<br/>PostgreSQL 16]
RenderJava -->|TCP| Upstash[Upstash<br/>Redis]
RenderJava -->|HTTPS| Groq[Groq API<br/>Llama + Whisper]
RenderJava -->|HTTPS + WebRTC| DID[D-ID Avatar API]
Service Hosting Plan
Component	Platform	Plan	Public?
Frontend - Next.js	Vercel	Free Hobby	Yes
Backend - Java Spring Boot	Render	Free / Starter	Yes
AI Service - Python FastAPI	Render	Free	No - Internal
MCP Server - Python	Render Private Service	Free / Starter	No - Never Public
PostgreSQL	Supabase	Free	No
Redis	Upstash	Free	No
Code Repository	GitHub	Free	Yes
Solo Developer Tip: Render Free Tier sleeps after 15 mins of inactivity. Open your backend health link 5 minutes before every demo/judging.

3. Prerequisites Before Deployment
   All P0 test cases pass locally - 15_QA_TESTING.md Regression Pack ✅
   Code pushed to GitHub main branch, .env NOT in repo
   Accounts created: Vercel, Render, Supabase, Upstash
   API Keys ready: GROQ_API_KEY, DID_API_KEY
   Local Golden Flow works: Login -> Voice -> Avatar -> Mood Dashboard
   Freeze Rule: No new features during deployment. Only deployment config and blocker fixes.

4. Deployment Order - CRITICAL
   Deploy in this exact order. Each step gives you a URL/value needed for the next step.

1. Supabase PostgreSQL
2. Upstash Redis
3. MCP Server (Render Private)
4. Python Sign Service (Render)
5. Java Backend (Render)
6. Frontend (Vercel)
7. Connect Frontend <-> Backend (CORS Fix + Redeploy Java)
8. Full Production Testing

5. Step 1: Deploy Database - Supabase
   Go to
   supabase.com
   -> New Project -> Name: saarthi-ai
   Set a strong Database Password and save it safely
   Go to Settings -> Database -> Connection String -> JDBC
   Copy values:
   Host: db.xxxxxxxxxxxx.supabase.co
   Port: 5432
   Database: postgres
   User: postgres
   Password: your saved password
   Your Flyway migration V1__init_schema.sql will automatically create all tables when Java Backend starts for the first time. You do not need to run SQL manually.

6. Step 2: Deploy Cache - Upstash Redis
   Go to
   upstash.com
   -> Create Database -> Name: saarthi-redis
   Select region closest to your Render region (e.g., US East)
   Copy: Host, Port, Password
   Enable TLS - Upstash requires secure connection in production
7. Step 3 & 4: Deploy Python Services
   7.1 Add Dockerfile / Runtime Files
   For ai-service-python/ and mcp-server-python/, ensure these files exist:

requirements.txt - already created during setup

Start command for Sign Service:

bash

uvicorn main:app --host 0.0.0.0 --port $PORT
Start command for MCP Server:

bash

python server.py
7.2 Deploy on Render
Render Dashboard -> New -> Web Service (Sign) / Private Service (MCP)
Connect your GitHub repo
Settings:
Root Directory: ai-service-python / mcp-server-python
Runtime: Python 3
Build Command: pip install -r requirements.txt
Start Command: as above
Add Environment Variables for MCP Server ONLY:
env

DB_HOST=db.xxxxxxxxxxxx.supabase.co
DB_NAME=postgres
DB_USERNAME=postgres
DB_PASSWORD=your_supabase_password
DB_SSLMODE=require
MCP_HOST=0.0.0.0
MCP uses a READ-ONLY DB user in ideal production. For hackathon MVP, using main user is acceptable but never expose MCP URL publicly. Render injects PORT itself - do not set it. MCP_HOST=0.0.0.0 lets the private service accept Render-internal traffic.

Deploy and note URLs:
Sign Service: https://saarthi-sign.onrender.com
MCP Service: Internal Render URL
Verify: Open https://saarthi-sign.onrender.com/health -> {"status":"UP"}

8. Step 5: Deploy Java Backend
   8.1 Create Dockerfile
   Create backend-java/Dockerfile:

dockerfile

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests
​
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
Create backend-java/.dockerignore:

target/
.env
.idea/
*.md

8.2 Deploy on Render
Render -> New -> Web Service -> Connect GitHub repo

Settings:

Root Directory: backend-java
Runtime: Docker
Instance Type: Free / Starter
Add ALL Environment Variables in Render Dashboard:

env

DB_HOST=db.xxxxxxxxxxxx.supabase.co
DB_PORT=5432
DB_NAME=postgres
DB_USERNAME=postgres
DB_PASSWORD=your_supabase_password
DB_JDBC_PARAMS=?sslmode=require
​
REDIS_HOST=your-upstash-host.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=your_upstash_password
REDIS_SSL_ENABLED=true
​
GROQ_API_KEY=gsk_xxxxxxxxxxxx
DID_API_KEY=xxxxxxxxxxxx
JWT_SECRET=your_super_secret_key_at_least_32_characters_long
​
FRONTEND_URL=https://localhost:3000
PYTHON_SERVICE_URL=https://saarthi-sign.onrender.com
MCP_SERVER_URL=http://saarthi-mcp:8001
MCP_ENABLED=true
AVATAR_PUBLIC_BASE_URL=https://saarthi-api.onrender.com
​
Do NOT set PORT - Render injects it and the app reads it automatically.
FRONTEND_URL is temporary. You will update it after Vercel deployment in Step 7.

Deploy. First build takes 5-10 minutes.
Verify: Open https://saarthi-api.onrender.com/actuator/health
json

{ "status": "UP", "components": { "db": {"status":"UP"}, "redis": {"status":"UP"} } }
If this fails, check Render Logs for the first Caused by: error - refer to 16_ERRORS_AND_DEBUGGING.md Section 15.

Note your backend URL: https://saarthi-api.onrender.com

9. Step 6: Deploy Frontend - Vercel
   Go to
   vercel.com
   -> Add New Project -> Import your GitHub repo
   Settings:
   Root Directory: frontend-nextjs
   Framework Preset: Next.js (auto-detected)
   Build Command: npm run build
   Add Environment Variables:
   env

NEXT_PUBLIC_API_URL=https://saarthi-api.onrender.com/api/v1
NEXT_PUBLIC_WS_URL=wss://saarthi-api.onrender.com/ws/mentor
Critical: Production MUST use https:// and wss://. Using http:// / ws:// will be blocked by browsers.

Deploy. Note your frontend URL: https://saarthi-ai.vercel.app
10. Step 7: Connect Frontend & Backend - CORS Fix
    This is the most missed step. Your Java backend currently only allows localhost:3000.

Go to Render -> Java Service -> Environment
Update:
env

FRONTEND_URL=https://saarthi-ai.vercel.app
Save - Render will auto-redeploy Java (3-5 mins)
Wait for /actuator/health to return UP again
Now Vercel can legally call Render. CORS error is resolved.

11. Step 8: Production Verification
    Run the Full Golden Flow from 14_SYSTEM_INTEGRATION.md on the LIVE URLs, not localhost:

Open https://saarthi-ai.vercel.app on Chrome Desktop
Register a new test user judge@test.com
Login successfully, JWT stored
Upload mentor photo -> Avatar READY < 60s
Start Live Session, allow Camera/Mic
Emotion badge updates live
Speak in Hinglish -> Avatar replies with lip-sync in < 3s
Show HELP sign -> Mentor responds
End session -> Mood visible on Dashboard
Send crisis test phrase -> Helpline modal appears
Logout -> Old token rejected
Repeat entire flow on Chrome Android phone
Also verify security in production:

Browser Network Tab: No GROQ_API_KEY or DID_API_KEY visible
Browser Network Tab: No video frames sent to server
GitHub Repo: No .env file committed
If any step fails in production but passed locally, check 16_ERRORS_AND_DEBUGGING.md Section 15 - it is 99% an Environment Variable or http vs https issue.

12. CI/CD Setup (Automatic Deployment)
    After manual deployment works, enable auto-deploy:

Service	Setting
Vercel	Auto-deploys on every push to main by default ✅
Render	Dashboard -> Settings -> Auto-Deploy: On Commit ✅
Git Flow for Solo Dev:

bash

git add .
git commit -m "fix: production cors config"
git push origin main
# Vercel + Render will auto-build and deploy in ~5 mins
Never push directly before a demo. Push, wait for both deploys to show Live, then re-run Regression Pack.

13. Environment Variable Master List
    Keep this table updated. This is your single source of truth.

Variable	Local	Vercel	Render Java	Render Python/MCP
NEXT_PUBLIC_API_URL	localhost:8080	✅ Prod URL	-	-
NEXT_PUBLIC_WS_URL	ws://localhost	✅ wss:// Prod	-	-
DB_*	Docker values	-	✅ Supabase	✅ MCP Only
REDIS_*	Docker values	-	✅ Upstash	-
GROQ_API_KEY	✅	❌ NEVER	✅	-
DID_API_KEY	✅	❌ NEVER	✅	-
JWT_SECRET	✅	❌ NEVER	✅	-
FRONTEND_URL	localhost:3000	-	✅ Vercel URL	-
PYTHON_SERVICE_URL	localhost:8000	-	✅ Render URL	-
MCP_SERVER_URL	localhost:8001	-	✅ Internal URL	-
DB_JDBC_PARAMS	(unset)	-	✅ ?sslmode=require	-
REDIS_SSL_ENABLED	(unset)	-	✅ true	-
DB_SSLMODE	(unset)	-	-	✅ require (MCP)
MCP_HOST	(unset)	-	-	✅ 0.0.0.0 (MCP)
AVATAR_PUBLIC_BASE_URL	localhost:8080	-	✅ Render backend URL	-
SIGN_ENABLED / MCP_ENABLED	true	-	✅ true (kill switches)	-
14. Handling Cold Starts & Demo Day Prep
    Render Free Tier problem: First request after sleep takes 30-60 seconds.

Demo Day Playbook (2nd Nov 2026):

8:00 AM - Open https://saarthi-api.onrender.com/actuator/health to wake backend
Open Vercel link, login with demo account, do one warm-up conversation
Keep both tabs open. Refresh health link every 10 minutes.
If possible, upgrade Render Java service to Starter ($7) for submission week - it never sleeps. This is the best money you can spend.
Keep your recorded 2-minute backup demo video ready offline.
15. Monitoring & Logs in Production
    What	Where
    Frontend Errors	Vercel Dashboard -> Deployments -> Logs
    Java Errors & Latency	Render Dashboard -> Java Service -> Logs
    DB Health	Supabase Dashboard -> Database -> Health
    API Usage / Limits	Groq Console, D-ID Studio Dashboard
    During judging, keep Render Java Logs open in a separate tab filtered for your session ID.

16. Rollback Plan
    If a new deployment breaks production:

Vercel Rollback: Deployments -> Previous Deployment -> Promote to Production (1 click, 10 seconds)

Render Rollback: Deploys -> Previous Deploy -> Rollback to this deploy

Database Rollback: Do NOT run destructive Flyway changes after production data exists. Create a new migration V2__...sql, never edit V1.

Emergency Kill Switches in application.yml:

yaml

saarthi:
mcp:
enabled: false # If MCP breaks production
agents:
avatar:
provider: static # If D-ID breaks/credits over
Change via Render Env Var + redeploy.

17. Custom Domain (Optional, If Time Permits)
    Buy domain / use free subdomain
    Vercel -> Project -> Settings -> Domains -> Add saarthi.yourdomain.com
    Update DNS as instructed by Vercel
    Update Render FRONTEND_URL to new domain and redeploy Java
    Not required for hackathon. vercel.app link is fully acceptable for judges.

18. Final Submission Checklist (Oct 31 - Nov 2)
    Oct 31, 2026
    Code freeze - no new features
    Final push to GitHub main
    Both Vercel & Render show Live deployment
    Golden Flow passes on live link (Desktop + Mobile)
    Nov 1, 2026
    Test live link with 3 friends as UAT judges
    Record / re-verify backup demo video
    Update README with Live Demo Link and Demo Video Link
    Submit hackathon form with GitHub + Live Link by night - do not wait for Nov 2
    Nov 2, 2026 - DEADLINE DAY
    Wake up all Render services in morning
    Only verify submission is received. Do not deploy new code today.
19. Common Deployment Failures
    Symptom	Fix
    Vercel build fails	Run npm run build locally first, fix TypeScript errors
    Render Java build fails	Check Dockerfile path & Root Directory = backend-java
    Health shows db: DOWN	Supabase password/host wrong in Render env
    CORS error in production	FRONTEND_URL in Render doesn't exactly match Vercel URL (no trailing slash)
    WebSocket fails in production	Frontend env still uses ws:// instead of wss://
    Avatar works locally, not in prod	DID_API_KEY missing in Render env
    Full fixes in 16_ERRORS_AND_DEBUGGING.md.

20. Definition of Done
    Deployment is complete when:

Frontend is live on Vercel and accessible publicly
Java Backend health is UP on Render with DB & Redis UP
Python & MCP services are deployed and reachable from Java
Full Golden Flow passes on live URLs on Desktop & Mobile
No secret/API key is exposed in frontend, GitHub, or browser
Auto-deploy from GitHub main is working
Backup demo video is recorded
Live Demo Link is added to README and hackathon submission
