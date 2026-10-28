"""Saarthi MCP server (Python, FastMCP).

One combined server for MVP per 13_MCP_CONNECTION.md: mood memory, user
profile, conversation summaries, and safety resources behind standard MCP
tools + resources, served over Streamable HTTP on port 8001.

Run (from mcp-server-python/, with THIS service's venv active):
    .venv/bin/python server.py
Inspect:
    npx @modelcontextprotocol/inspector http://localhost:8001/mcp

SECURITY
- user_id MUST come from Java (validated JWT), never from the frontend or
  from Llama's generation. Tools raise on a missing/blank user_id.
- Internal service only: never expose publicly, never call from the browser.
- Read-only DB access (see db.py). Production adds X-MCP-Internal-Key (V2).
"""

from __future__ import annotations

import json
import logging

from dotenv import load_dotenv
from mcp.server.fastmcp import FastMCP

import db

load_dotenv()
log = logging.getLogger("saarthi-mcp")

mcp = FastMCP("saarthi-mcp-server", host="127.0.0.1", port=8001)

HELPLINES_IN = [
    {"name": "Tele-MANAS", "phone": "14416", "available": "24x7"},
    {"name": "AASRA", "phone": "9820466726", "available": "24x7"},
    {"name": "iCall", "phone": "9152987821", "available": "Mon-Sat"},
]


def require_user(user_id: str) -> str:
    """Reject calls without a Java-injected identity. Raising (not returning
    an error string) fails the MCP call itself, so no data can leak."""
    if user_id is None or not str(user_id).strip():
        raise ValueError("user_id is required and must be injected by the Java backend")
    return str(user_id).strip()


@mcp.tool()
def get_recent_moods(user_id: str, days: int = 7) -> str:
    """Get user's mood history for the last N days. Use this to personalize mentoring."""
    require_user(user_id)
    window = min(30, max(1, int(days)))
    rows = db.recent_moods(user_id, window)
    log.info("[MCP] get_recent_moods user=%s days=%d rows=%d",
             user_id[-8:], window, len(rows))
    return json.dumps(rows)


@mcp.tool()
def get_conversation_summary(user_id: str, limit: int = 3) -> str:
    """Get summaries of the user's most recent mentoring sessions."""
    require_user(user_id)
    rows = db.conversation_summaries(user_id, min(10, max(1, int(limit))))
    log.info("[MCP] get_conversation_summary user=%s rows=%d", user_id[-8:], len(rows))
    return json.dumps(rows)


@mcp.tool()
def get_user_profile(user_id: str) -> str:
    """Get user's name, language and mentor preference for personalization."""
    require_user(user_id)
    profile = db.user_profile(user_id)
    if profile is None:
        return json.dumps({"error": "user_not_found"})
    return json.dumps(profile)


@mcp.tool()
def get_helpline_resources(country: str = "IN") -> str:
    """Get verified crisis helplines. Always use this in crisis situations."""
    if str(country).upper() != "IN":
        return json.dumps([])
    return json.dumps(HELPLINES_IN)


@mcp.resource("saarthi://user/{user_id}/mood-summary")
def mood_summary(user_id: str) -> str:
    """Human-readable 7-day mood summary for Llama context."""
    require_user(user_id)
    rows = db.recent_moods(user_id, 7)
    if not rows:
        return "No mood history recorded in the last 7 days."
    moods = ", ".join(r["mood"] for r in rows[:10])
    return f"Moods this week, most recent first: {moods}."


@mcp.resource("saarthi://safety/helplines/IN")
def helplines_in() -> str:
    """Verified India crisis helpline list."""
    return json.dumps(HELPLINES_IN)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    log.info("Starting saarthi-mcp-server on http://127.0.0.1:8001/mcp")
    mcp.run(transport="streamable-http")
