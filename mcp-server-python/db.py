"""Read-only PostgreSQL access for the Saarthi MCP server.

SECURITY: every statement in this file is a SELECT. The production database
role for this service must hold nothing but SELECT on users, mood_logs,
conversations, and messages (no INSERT/UPDATE/DELETE). Local dev reuses the
app credentials from .env for convenience only.
"""

from __future__ import annotations

import os

import psycopg2
import psycopg2.extras


def get_conn():
    return psycopg2.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=int(os.getenv("DB_PORT", "5432")),
        database=os.getenv("DB_NAME", "saarthi_db"),
        user=os.getenv("DB_USERNAME", "saarthi_user"),
        password=os.getenv("DB_PASSWORD", "saarthi_password"),
        connect_timeout=5,
        # "prefer" negotiates TLS with Supabase, stays plain locally.
        sslmode=os.getenv("DB_SSLMODE", "prefer"),
    )


def recent_moods(user_id: str, days: int) -> list[dict]:
    with get_conn() as conn, conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            "SELECT mood, confidence, logged_at FROM mood_logs "
            "WHERE user_id = %s AND logged_at >= NOW() - (%s || ' days')::interval "
            "ORDER BY logged_at DESC",
            (user_id, days),
        )
        return [
            {"mood": row["mood"], "confidence": row["confidence"],
             "logged_at": str(row["logged_at"])}
            for row in cur.fetchall()
        ]


def user_profile(user_id: str) -> dict | None:
    with get_conn() as conn, conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            "SELECT name, preferred_language FROM users WHERE id = %s",
            (user_id,),
        )
        row = cur.fetchone()
        if row is None:
            return None
        return {"name": row["name"], "preferred_language": row["preferred_language"]}


def conversation_summaries(user_id: str, limit: int) -> list[dict]:
    """Extractive session summaries: title, start, message count, and the
    opening user message. V2 may replace the excerpt with an abstractive LLM
    summary; the tool shape stays the same."""
    with get_conn() as conn, conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            "SELECT id, title, started_at FROM conversations "
            "WHERE user_id = %s ORDER BY started_at DESC LIMIT %s",
            (user_id, limit),
        )
        conversations = cur.fetchall()
        summaries = []
        for conv in conversations:
            cur.execute(
                "SELECT COUNT(*) AS n FROM messages WHERE conversation_id = %s",
                (conv["id"],),
            )
            count = cur.fetchone()["n"]
            cur.execute(
                "SELECT content FROM messages "
                "WHERE conversation_id = %s AND role = 'USER' "
                "ORDER BY created_at ASC LIMIT 1",
                (conv["id"],),
            )
            first = cur.fetchone()
            summaries.append({
                "conversation_id": str(conv["id"]),
                "title": conv["title"],
                "started_at": str(conv["started_at"]),
                "message_count": count,
                "opening_message": (first["content"][:200] if first else ""),
            })
        return summaries
