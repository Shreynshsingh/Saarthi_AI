package com.saarthi.ai.model;

/**
 * How the user message entered the system.
 * Must stay identical to the PostgreSQL {@code input_mode} enum.
 */
public enum InputMode {
    TEXT,
    VOICE,
    SIGN
}
