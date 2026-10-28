SETUP AND INITIALIZATION DOCUMENT
Project: Saarthi AI - AI Mentor Document Version: 1.0 Author: Shreynsh Singh (Solo Developer) Hackathon: Meta Global AI Developer Hackathon Deadline: 2nd November 2026

1. Purpose
   This document provides step-by-step instructions to set up, initialize, configure, and run the complete Saarthi AI project on a local machine from scratch.

After following this document, you will have:

PostgreSQL + Redis running
Java Spring Boot Backend running on 8080
Python FastAPI Service running on 8000
Next.js Frontend running on 3000
All services connected and health-checked
2. System Requirements
   2.1 Hardware
   Component	Minimum	Recommended
   RAM	8 GB	16 GB
   Storage	10 GB Free	20 GB Free
   Camera / Mic	Required for testing	HD Webcam
   2.2 Software Prerequisites
   Install these BEFORE starting:

Tool	Version	Download	Verify Command
Java JDK	21 LTS	adoptium.net	java -version
Maven	Wrapper Included	No separate install needed	./mvnw -v
Node.js	20 LTS	nodejs.org	node -v and npm -v
Python	3.11	python.org	python --version
Docker Desktop	Latest	docker.com	docker -v
Git	Latest	git-scm.com	git --version
VS Code / IntelliJ	Latest	-	IntelliJ recommended for Java
Windows Users: Enable WSL2 for Docker Desktop. Use PowerShell or Git Bash for all commands below.

3. API Keys Required
   Create free accounts and get these keys before coding. Store them ONLY in .env, never in GitHub.

Service	Purpose	Get It From	Required For MVP?
Groq API Key	Llama 3.3 + Whisper	console.groq.com	YES - Day 1
D-ID API Key	Live Avatar	studio.d-id.com	Yes - Phase 3
Supabase	Prod PostgreSQL	supabase.com	Only for Deployment
Upstash	Prod Redis	upstash.com	Only for Deployment
4. Project Initialization
   4.1 Create Repository
   bash

mkdir saarthi-ai
cd saarthi-ai
git init
git branch -M main
Create this folder structure:

saarthi-ai/
├── frontend-nextjs/
├── backend-java/
├── ai-service-python/
├── docs/
├── docker-compose.yml
├── .gitignore
└── README.md

4.2 Create .gitignore in Root
Add this file immediately to protect your secrets:

gitignore

# Secrets
.env
.env.local
.env*.local
*.env
​
# Java
target/
*.class
​
# Node
node_modules/
.next/
out/
​
# Python
__pycache__/
venv/
.venv/
​
# IDE & OS
.idea/
.vscode/
.DS_Store
​
# Uploads & Logs
uploads/
*.log
5. Step 1: Initialize Database Layer
   Create docker-compose.yml in project root:

yaml

version: '3.8'
services:
postgres:
image: postgres:16
container_name: saarthi-postgres
environment:
POSTGRES_DB: saarthi_db
POSTGRES_USER: saarthi_user
POSTGRES_PASSWORD: saarthi_password
ports:
- "5432:5432"
volumes:
- postgres_data:/var/lib/postgresql/data
​
redis:
image: redis:7-alpine
container_name: saarthi-redis
ports:
- "6379:6379"
volumes:
- redis_data:/data
​
volumes:
postgres_data:
redis_data:
Start & Verify:

bash

docker-compose up -d
docker ps
# Both saarthi-postgres and saarthi-redis must show 'Up'
6. Step 2: Initialize Backend (Java)
   6.1 Generate Project
   Go to
   start.spring.io
   and select:

Project: Maven, Language: Java, Spring Boot: 3.2.x, Java: 21
Group: com.saarthi, Artifact: ai, Packaging: Jar
Dependencies: Spring WebFlux, Spring for WebSocket, Spring Data JPA, PostgreSQL Driver, Spring Data Redis, Spring Security, Validation, Actuator, Lombok
Download, unzip into backend-java/ folder.

6.2 Configure Environment
Create backend-java/.env:

env

DB_HOST=localhost
DB_PORT=5432
DB_NAME=saarthi_db
DB_USERNAME=saarthi_user
DB_PASSWORD=saarthi_password
REDIS_HOST=localhost
REDIS_PORT=6379
GROQ_API_KEY=your_groq_key_here
DID_API_KEY=your_did_key_here
JWT_SECRET=your_super_secret_key_at_least_32_characters_long
FRONTEND_URL=http://localhost:3000
PYTHON_SERVICE_URL=http://localhost:8000
Also create .env.example with same keys but empty values. This file IS safe to commit to GitHub.

6.3 Verify Backend Initialization
bash

cd backend-java
./mvnw clean install -DskipTests
./mvnw spring-boot:run
Success Check: Open http://localhost:8080/actuator/health

json

{ "status": "UP", "components": { "db": {"status":"UP"}, "redis": {"status":"UP"} } }
If you see this, Backend + DB initialization is complete. Full DB config details are in 06_DATABASE_CONNECTION.md.

7. Step 3: Initialize Frontend (Next.js)
   Open a NEW terminal:

bash

npx create-next-app@latest frontend-nextjs --typescript --tailwind --eslint --app --src-dir=false --import-alias "@/*"
cd frontend-nextjs
npm install framer-motion
Create frontend-nextjs/.env.local:

env

NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws/mentor
Run & Verify:

bash

npm run dev
Open http://localhost:3000 - you should see the default Next.js page.

8. Step 4: Initialize AI Service (Python)
   Open a NEW terminal:

bash

cd ai-service-python
python -m venv venv
​
# Windows:
venv\Scripts\activate
# Mac/Linux:
source venv/bin/activate
​
pip install fastapi uvicorn python-dotenv pydantic numpy
pip freeze > requirements.txt
Create main.py:

python

from fastapi import FastAPI
app = FastAPI(title="Saarthi AI Service")
​
@app.get("/health")
def health():
return {"status": "UP", "service": "sign-language-agent"}
​
@app.post("/predict-sign")
def predict_sign(data: dict):
# TODO: Replace with MediaPipe LSTM model in Phase 4
return {"sign": "HELP", "confidence": 0.94}
Run & Verify:

bash

uvicorn main:app --reload --port 8000
Open http://localhost:8000/health -> {"status": "UP"}

9. Full System Startup Order
   Every day you code, start services in this exact order. Use 4 separate terminals:

bash

# Terminal 1: Database
docker-compose up -d
​
# Terminal 2: Python AI Service
cd ai-service-python && uvicorn main:app --reload --port 8000
​
# Terminal 3: Java Backend
cd backend-java && ./mvnw spring-boot:run
​
# Terminal 4: Frontend
cd frontend-nextjs && npm run dev
Service Map:

Service	Local URL	Health Check
Frontend
http://localhost:3000
Browser opens
Java Backend
http://localhost:8080
/actuator/health
Python Service
http://localhost:8000
/health and /docs
PostgreSQL	localhost:5432	Via DBeaver
Redis	localhost:6379	docker exec -it saarthi-redis redis-cli ping -> PONG
10. First Commit
    Once all 4 services are UP:

bash

cd saarthi-ai
git add .
git commit -m "chore: initial project setup with java, nextjs, python and docker"
git remote add origin https://github.com/YOUR_USERNAME/saarthi-ai.git
git push -u origin main
11. Common Initialization Errors
    Error	Cause	Fix
    Port 8080 already in use	Old Java process running	Kill process or change server.port in application.yml
    Port 3000 already in use	Old Next.js running	Ctrl+C old terminal, restart npm run dev
    Connection refused: 5432	Docker not started	Start Docker Desktop, run docker-compose up -d
    GROQ_API_KEY is null	.env not loaded	Check .env is in backend-java/ root, restart backend
    venv not activated	Python packages not found	Run activate command in Step 4 again, prompt should show (venv)
    docker-compose: command not found	Docker not installed / not in PATH	Reinstall Docker Desktop and restart terminal
12. Daily Shutdown
    bash

# Stop Frontend, Backend, Python with Ctrl+C in each terminal
docker-compose down
# Note: Use 'docker-compose down -v' ONLY if you want to delete all DB data and start fresh
13. Definition of Done - Setup Complete
    Check all boxes before starting Module 1 coding:

java -version, node -v, python --version, docker -v all work
docker ps shows postgres and redis containers UP
Backend health endpoint shows db: UP, redis: UP
Frontend opens on localhost:3000 without error
Python /docs page opens on localhost:8000/docs
.env files are created locally and are listed in .gitignore
First commit pushed to GitHub, and .env is NOT visible in GitHub repo
