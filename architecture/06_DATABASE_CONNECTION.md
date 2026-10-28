DATABASE CONNECTION DOCUMENT
Project: Saarthi AI - AI Mentor Document Version: 1.0 Author: Shreynsh Singh Backend: Java 21 + Spring Boot 3.2 Database: PostgreSQL 16 (Primary) + Redis 7 (Cache/Session) Deadline: 2nd November 2026

1. Overview
   We use 2 data stores for 2 different jobs:

Database	Job	Data Stored
PostgreSQL	Permanent Storage	Users, Mentor Profiles, Conversations, Messages, Mood Logs
Redis	Temporary / Fast Storage	Live WebSocket Sessions, JWT blacklist, Llama response cache, Rate Limiting
Python AI Service will NEVER connect directly to the database. Only Java Backend talks to the DB. This keeps security clean.

Next.js -> Spring Boot (Java) -> PostgreSQL
-> Redis

2. Prerequisites
   Java 21 installed
   Maven installed
   Docker Desktop installed
   DBeaver / pgAdmin 4 installed (to view tables)
3. Step 1: Run Database Locally with Docker
   Create docker-compose.yml in your project root:

yaml

version: '3.8'
​
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
Run it:

bash

docker-compose up -d
docker ps # verify both containers are running
4. Step 2: Add Dependencies in Java
   Add this to your pom.xml:

xml

<!-- Database -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
​
<!-- Redis Cache -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
​
<!-- DB Migration -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
​
<!-- Health Check -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
5. Step 3: Configure application.yml
CRITICAL RULE: Never hardcode passwords in code. Use Environment Variables.

Create .env file (add to .gitignore):

env

DB_HOST=localhost
DB_PORT=5432
DB_NAME=saarthi_db
DB_USERNAME=saarthi_user
DB_PASSWORD=saarthi_password
REDIS_HOST=localhost
REDIS_PORT=6379
Now src/main/resources/application.yml:

yaml

spring:
application:
name: saarthi-backend

datasource:
url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:saarthi_db}
username: ${DB_USERNAME:saarthi_user}
password: ${DB_PASSWORD:saarthi_password}
driver-class-name: org.postgresql.Driver
hikari:
maximum-pool-size: 10
minimum-idle: 2
connection-timeout: 30000
pool-name: SaarthiHikariPool

jpa:
hibernate:
ddl-auto: validate # Flyway will create tables, JPA will only validate
show-sql: true
properties:
hibernate:
format_sql: true
dialect: org.hibernate.dialect.PostgreSQLDialect

data:
redis:
host: ${REDIS_HOST:localhost}
port: ${REDIS_PORT:6379}
timeout: 2000ms

flyway:
enabled: true
locations: classpath:db/migration
baseline-on-migrate: true

management:
endpoints:
web:
exposure:
include: health,info
endpoint:
health:
show-details: always


6. Step 4: Create Tables with Flyway Migration
   Create file: src/main/resources/db/migration/V1__init_schema.sql

sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
​
CREATE TYPE mood_type AS ENUM ('HAPPY','SAD','ANXIOUS','STRESSED','TIRED','NEUTRAL','ANGRY');
CREATE TYPE input_mode AS ENUM ('TEXT','VOICE','SIGN');
​
CREATE TABLE users (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
name VARCHAR(100) NOT NULL,
email VARCHAR(255) UNIQUE NOT NULL,
password_hash VARCHAR(255) NOT NULL,
preferred_language VARCHAR(10) DEFAULT 'en-IN',
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
​
CREATE TABLE mentor_profiles (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID REFERENCES users(id) ON DELETE CASCADE,
mentor_name VARCHAR(100) DEFAULT 'Saarthi',
avatar_image_url TEXT,
avatar_provider_id TEXT,
voice_id VARCHAR(100),
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
​
CREATE TABLE conversations (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID REFERENCES users(id) ON DELETE CASCADE,
title VARCHAR(255),
started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
ended_at TIMESTAMP
);
​
CREATE TABLE messages (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
conversation_id UUID REFERENCES conversations(id) ON DELETE CASCADE,
role VARCHAR(20) NOT NULL,
content TEXT NOT NULL,
input_mode input_mode DEFAULT 'TEXT',
detected_emotion mood_type,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
​
CREATE TABLE mood_logs (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID REFERENCES users(id) ON DELETE CASCADE,
conversation_id UUID REFERENCES conversations(id),
mood mood_type NOT NULL,
confidence FLOAT,
note TEXT,
logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
​
CREATE INDEX idx_messages_conversation ON messages(conversation_id);
CREATE INDEX idx_mood_user_date ON mood_logs(user_id, logged_at DESC);
CREATE INDEX idx_users_email ON users(email);
When you start Spring Boot, Flyway will automatically run this and create all tables.

7. Step 5: Create JPA Entities
   Example User.java:

java

package com.saarthi.ai.model;
​
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
​
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
​
@Column(nullable = false, length = 100)
private String name;
​
@Column(unique = true, nullable = false)
private String email;
​
@Column(name = "password_hash", nullable = false)
private String passwordHash;
​
@Column(name = "preferred_language")
private String preferredLanguage = "en-IN";
​
@Column(name = "created_at", updatable = false)
private LocalDateTime createdAt = LocalDateTime.now();
}
Create similar entities for MentorProfile, Conversation, Message, MoodLog.

For Enum in Message/MoodLog:

java

@Enumerated(EnumType.STRING)
@Column(name = "detected_emotion")
private MoodType detectedEmotion;
8. Step 6: Create Repositories
   java

package com.saarthi.ai.repository;
​
import com.saarthi.ai.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
​
public interface UserRepository extends JpaRepository<User, UUID> {
Optional<User> findByEmail(String email);
boolean existsByEmail(String email);
}
java

public interface MoodLogRepository extends JpaRepository<MoodLog, UUID> {
List<MoodLog> findByUserIdOrderByLoggedAtDesc(UUID userId, Pageable pageable);
}
Create ConversationRepository, MessageRepository, MentorProfileRepository the same way. Spring Data JPA will auto-implement them.

9. Step 7: Configure Redis
   Create config/RedisConfig.java:

java

package com.saarthi.ai.config;
​
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
​
@Configuration
@EnableCaching
public class RedisConfig {
​
@Bean
public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
RedisTemplate<String, String> template = new RedisTemplate<>();
template.setConnectionFactory(factory);
template.setKeySerializer(new StringRedisSerializer());
template.setValueSerializer(new StringRedisSerializer());
return template;
}
}
Usage: Store live session redisTemplate.opsForValue().set("session:" + userId, conversationId);

10. Step 8: Test The Connection
    Start DB: docker-compose up -d
    Start Backend: ./mvnw spring-boot:run
    Check Logs - You should see:
    HikariPool-1 - Start completed.
    Flyway: Successfully applied 1 migration
    Started SaarthiApplication in 4.2 seconds

Check Health Endpoint: Open http://localhost:8080/actuator/health
json

{
"status": "UP",
"components": {
"db": { "status": "UP", "details": { "database": "PostgreSQL" } },
"redis": { "status": "UP" }
}
}
If you see this, your connection is 100% successful.

Verify in DBeaver: Connect to localhost:5432 / saarthi_db and check Tables folder.
11. Production Connection (Deployment)
    For hackathon deployment, use Supabase (Free PostgreSQL) and Upstash (Free Redis).

Go to supabase.com -> Create Project -> Settings -> Database
Copy the JDBC URL. It will look like: jdbc:postgresql://db.xxxxx.supabase.co:5432/postgres
In Render/Railway, set Environment Variables:
DB_HOST=db.xxxxx.supabase.co
DB_NAME=postgres
DB_USERNAME=postgres
DB_PASSWORD=your_supabase_password
REDIS_HOST=your_upstash_host

No code change needed. Your application.yml will automatically pick production values.

12. Common Errors & Fixes
    Error	Cause	Fix
    Connection refused: localhost:5432	Docker not running	Run docker-compose up -d
    password authentication failed	Wrong password in .env	Match password with docker-compose.yml
    Table "users" does not exist	Flyway didn't run	Check V1__init_schema.sql is in correct folder, delete DB volume and restart
    Unable to connect to Redis	Redis container down	docker ps check, restart redis
    Hibernate ddl-auto validate failed	Entity field doesn't match SQL column	Match Java field name exactly with @Column(name="...")
    Pro Tip for Solo Dev: If Flyway gets stuck, run docker-compose down -v to delete the volume and start fresh.

13. Definition of Done Checklist
    docker-compose up -d runs Postgres & Redis without error
    Spring Boot starts with HikariPool success log
    /actuator/health shows db: UP and redis: UP
    All 5 tables visible in DBeaver/pgAdmin
    Saving a test User via Repository works
    No password is hardcoded in GitHub code
    Your DB layer is now production-ready and scalable. This setup will support your WebSocket handler to save every message and mood log in real-time.