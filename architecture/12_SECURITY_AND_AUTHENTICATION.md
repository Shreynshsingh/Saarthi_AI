# SECURITY AND AUTHENTICATION DOCUMENT

**Project:** Saarthi AI - AI Mentor
**Document Version:** 1.0
**Author:** Shreynsh Singh (Solo Developer)
**Hackathon:** Meta Global AI Developer Hackathon
**Deadline:** 2nd November 2026
**Backend:** Java 21 + Spring Boot 3.2 + Spring Security + JWT
**Related Docs:** `PRD.md`, `ARCHITECTURE.md`, `06_DATABASE_CONNECTION.md`, `07_API_INTEGRATION.md`

---

## 1. Purpose

This document defines how to design and implement the complete Security and Authentication layer for Saarthi AI.

Saarthi handles highly sensitive data:
- User's personal account & conversations about stress/anxiety
- Webcam facial expressions & microphone voice
- Uploaded photos for personalized avatar
- Mood history

Security is not an addon feature. It is the trust foundation of this product.

After implementing this document:
- Only logged-in users can access mentor, avatar, and mood data
- No user can access another user's conversations or avatar
- Passwords and API keys are never exposed
- Real-time WebSocket sessions are authenticated and private
- Camera video never leaves the user's browser

## 2. Security Principles

1.  **Authentication First:** Every API and WebSocket connection must prove identity via JWT.
2.  **Least Privilege:** A logged-in user can access ONLY their own data. Always filter by `userId` from JWT, never trust `userId` sent by frontend.
3.  **Never Trust Frontend:** All validation, crisis checks, and ownership checks happen in Java Backend.
4.  **Secrets Stay in Backend:** Groq, D-ID, DB passwords exist ONLY in backend `.env`. Frontend has zero AI keys.
5.  **Privacy By Design:** Raw video is processed in-browser via MediaPipe. Server receives only emotion label.
6.  **Secure By Default:** All passwords hashed, all transport encrypted (HTTPS/WSS in production), all inputs validated.

## 3. Security Architecture Overview

`
graph TD
    FE[Next.js Frontend] -->|1. Login: email+password| AUTH[AuthController]
    AUTH -->|2. Verify BCrypt Hash| DB[(PostgreSQL - users)]
    AUTH -->|3. Generate JWT| FE
    FE -->|4. API Call + Bearer JWT| FILTER[JwtAuthFilter]
    FILTER -->|5. Validate JWT| SEC[Spring Security Context]
    SEC -->|6. Allow / Deny| API[Protected Controllers]
    FE -->|7. WSS Connect ?token=JWT| WS[WebSocket Handler]
    WS -->|8. Validate JWT Before Session| API
    API --> REDIS[(Redis - Blacklist / Rate Limit)]

Technology Choices:

Need	Technology
Authentication Framework	Spring Security 6
Token Type	JWT (JSON Web Token)
Password Hashing	BCrypt (strength 12)
Token Library	JJWT 0.12.x
Transport Security	HTTPS + WSS (TLS) in production
Session Revocation	Redis Token Blacklist
Rate Limiting	Redis
4. User Data Model
From 06_DATABASE_CONNECTION.md - users table:

sql

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    preferred_language VARCHAR(10) DEFAULT 'en-IN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
Rules:

password_hash NEVER stores plain password
email is UNIQUE and stored in lowercase
No role table for MVP. Every registered user has role USER. Add ADMIN only in V2 if needed.
5. Dependencies (pom.xml)
Add to backend-java/pom.xml:

xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
6. Configuration
application.yml addition:

yaml

saarthi:
  security:
    jwt:
      secret: ${JWT_SECRET} # From .env, min 32 characters
      expiration-ms: 86400000 # 24 hours for hackathon
    cors:
      allowed-origin: ${FRONTEND_URL:http://localhost:3000}
.env (never commit):

env

JWT_SECRET=your_super_secret_key_at_least_32_characters_long_change_this
Generate a strong secret: openssl rand -base64 64

7. Password Security
7.1 Rules
Minimum 8 characters
Hashed with BCrypt(12) before saving - implemented via PasswordEncoder bean
Plain password is never logged, stored, or returned in any API response
7.2 Implementation
java

// config/SecurityConfig.java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
During registration: user.setPasswordHash(passwordEncoder.encode(request.password())) During login: passwordEncoder.matches(rawPassword, user.getPasswordHash())

8. JWT Design
8.1 Token Structure
Payload contains ONLY non-sensitive identifiers:

json

{
  "sub": "user-uuid",
  "email": "shreynsh@example.com",
  "iat": 1729000000,
  "exp": 1729086400
}
Never put password, mood data, or conversation content inside JWT.

8.2 JwtService Implementation
Create service/JwtService.java:

java

@Service
public class JwtService {
    @Value("${saarthi.security.jwt.secret}")
    private String secret;
​
    @Value("${saarthi.security.jwt.expiration-ms}")
    private long expirationMs;
​
    public String generateToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
​
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token);
            return true;
        } catch (JwtException e) { return false; }
    }
​
    public UUID extractUserId(String token) {
        String sub = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(token).getPayload().getSubject();
        return UUID.fromString(sub);
    }
}
8.3 Token Lifecycle
Issue on Login (24h) -> Frontend stores it -> Sent with every request -> On expiry, backend returns 401 -> Frontend redirects to Login. Refresh Token is OUT OF SCOPE for MVP, add in V2.

9. Spring Security Configuration
Create config/SecurityConfig.java:

java

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
​
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable()) // Stateless JWT API, no session cookies
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                .requestMatchers("/ws/**").permitAll() // JWT validated manually in WS handshake
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
9.1 JwtAuthFilter
Create security/JwtAuthFilter.java. It runs on EVERY REST request:

Read Authorization: Bearer <token> header
If missing/invalid/blacklisted -> return 401
If valid -> extract userId, set SecurityContext, allow request
10. Authentication Flows
10.1 Registration - POST /api/v1/auth/register
Request DTO with validation:

java

public record RegisterRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    @Size(min = 8, message = "Password must be at least 8 characters") String password,
    String preferredLanguage
) {}
Flow: Validate -> Check existsByEmail -> Hash password -> Save user -> Return user info (NO token, NO password)

Error: Duplicate email returns 400 { "success": false, "message": "Email already exists" }

10.2 Login - POST /api/v1/auth/login
Flow: Find by email -> passwordEncoder.matches() -> If fail, return generic 401 "Invalid email or password" (never reveal which one is wrong) -> If success, generate JWT -> Return { token, userId, name }

10.3 Logout - POST /api/v1/auth/logout
Flow: Frontend sends JWT -> Java adds token to Redis blacklist with TTL = remaining token life -> Frontend deletes token locally Redis Key: blacklist:token:<token_hash> JwtAuthFilter checks this blacklist on every request.

11. Protected API Matrix
Endpoint	Access	Ownership Rule
POST /api/v1/auth/register, /login	Public	-
GET /actuator/health	Public	No sensitive details exposed
POST /api/v1/mentor/upload-avatar	Authenticated	Saves avatar only for JWT user
GET /api/v1/mentor/my-mentor	Authenticated	Returns only JWT user's mentor
GET /api/v1/conversations	Authenticated	WHERE user_id = jwtUserId
GET /api/v1/mood/history	Authenticated	WHERE user_id = jwtUserId
WS /ws/mentor	Authenticated via token	Session bound to JWT user
Critical Implementation Rule: Controllers get userId ONLY like this, never from request body:

java

UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
12. WebSocket Security (Most Important & Different)
Browser WebSocket API cannot send custom headers, so we authenticate via query param.

Connection URL:

ws://localhost:8080/ws/mentor?token=<JWT_TOKEN>

Implementation in MentorWebSocketHandler / Handshake Interceptor:

Extract token from URI query
Validate with JwtService.isTokenValid() + check Redis blacklist
If invalid -> Close connection with code 4401 Unauthorized immediately, before any message
If valid -> Store userId in WebSocket session attributes and use it for the entire session
Never accept userId from a WebSocket message payload. Always use the session's authenticated userId.

13. CORS Configuration
Only your frontend may call your backend.

java

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(frontendUrl)); // http://localhost:3000 in dev, Vercel URL in prod
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
Never use setAllowedOrigins(List.of("*")) with credentials in production.

14. Frontend Integration (Next.js)
Store token: On login success, save to localStorage.setItem('saarthi_token', token) (Acceptable for hackathon MVP. Production V2: migrate to httpOnly cookie.)

REST calls:

typescript

const token = localStorage.getItem('saarthi_token');
fetch(`${process.env.NEXT_PUBLIC_API_URL}/mood/history?days=7`, {
  headers: { Authorization: `Bearer ${token}` }
});
WebSocket:

typescript

const ws = new WebSocket(`${process.env.NEXT_PUBLIC_WS_URL}?token=${token}`);
Global 401 Handling: Create a fetch wrapper / interceptor. On any 401 response: clear token, redirect to /login. This handles expired tokens gracefully.

Route Protection: Create middleware / protected layout that checks for token before rendering /dashboard, /session, /mentor/create. If absent, redirect to /login.

15. File Upload Security (Avatar Photo)
Endpoint POST /api/v1/mentor/upload-avatar is high-risk. Implement all checks in Java before calling D-ID:

Auth: JWT required
Type Whitelist: Only image/jpeg, image/png, image/webp allowed
Size Limit: Max 5 MB (spring.servlet.multipart.max-file-size=5MB)
Validation: Verify file header/magic bytes, not just extension
Safe Naming: Rename uploaded file to UUID + extension. Never use original filename in storage path (prevents path traversal)
Ownership: Saved mentor_profile is linked only to JWT user
Reject all other files with 400.

16. Rate Limiting & Abuse Protection (Redis)
Protect login and mentor APIs from brute force using Redis counters:

API	Limit	Window
POST /auth/login	5 attempts / email+IP	15 minutes
WS USER_MESSAGE	20 messages / user	1 minute
POST /mentor/upload-avatar	5 uploads / user	1 hour
On limit exceed, return 429 { "success": false, "message": "Please wait a moment and try again." } - never expose internal limits.

17. Data Privacy & Responsible AI Security
These are security requirements specific to Saarthi, mention them in your pitch:

Video Privacy: MediaPipe runs in-browser (WASM). Raw webcam frames are never transmitted or stored. Audit proof: Network tab shows only {emotion: "STRESSED", confidence: 0.87}.
Voice Privacy: Audio is sent only for transcription, not stored long-term. Do not save audio files in DB, only transcribed text in messages (with user consent via session start).
Data Deletion: V2 requirement, document intent now: user must be able to delete conversations. Add DELETE /api/v1/conversations/{id} if time permits - must verify ownership by JWT userId.
Disclaimer Enforcement: Crisis/safety logic runs server-side in SafetyAgent before Llama, so it cannot be bypassed by frontend manipulation.
No Sensitive Logging: Never log passwords, JWTs, full conversation text, or API keys. Log only userId, conversationId, agent latency.
18. Threat Model & Mitigations
Threat	Mitigation Implemented
Brute-force login	BCrypt + Redis rate limit on /login
Stealing another user's mood/chats	Every query filtered by JWT userId, ownership check
Fake userId in request body	Backend ignores body userId, uses SecurityContext only
Unauthenticated WebSocket abuse	JWT handshake validation, close 4401
Uploading malicious file as avatar	Type/size/magic-byte validation, UUID renaming
Leaked API keys in GitHub/Frontend	Keys only in backend .env, .env in .gitignore, Frontend never calls Groq/D-ID
Token reuse after logout	Redis blacklist checked in filter
Man-in-the-middle	HTTPS/WSS enforced in production (Vercel + Render TLS)
19. Testing Checklist
Test security in isolation before demo:

 Register with short password -> 400 validation error
 Register duplicate email -> 400
 Wrong password login -> 401 with generic message
 Correct login -> JWT returned, password never in response
 Call /mood/history without token -> 401
 Call /mood/history with expired/tampered token -> 401
 Call /mood/history with valid token -> Only own data returned
 Try to fetch another user's conversation ID -> 403/404, no data leaked
 Connect WebSocket without token -> Closed 4401
 Connect WebSocket with valid token -> Session works
 Logout, then reuse same token -> 401 (blacklisted)
 Upload .exe / 10MB file as avatar -> 400 rejected
 Check GitHub repo -> No .env, no API key, no password visible
 Browser Network tab during session -> No video frames sent, no Groq/D-ID key visible
20. Definition of Done
Security & Authentication implementation is complete when:

 User can register/login and receives a JWT
 All protected REST APIs return 401 without valid JWT
 WebSocket rejects unauthenticated connections
 User A cannot access User B's mentor, conversations, or mood under any test
 Passwords are BCrypt-hashed in PostgreSQL (verify in DBeaver: password_hash starts with $2a$12$)
 Logout blacklists token in Redis
 No secret exists in frontend code, GitHub, or browser console