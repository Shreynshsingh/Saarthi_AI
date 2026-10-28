package com.saarthi.ai.model;

/**
 * Mood values. Must stay identical to the PostgreSQL {@code mood_type} enum,
 * the Java entities, and the TypeScript frontend (see integration contracts).
 */
public enum MoodType {
    HAPPY,
    SAD,
    ANXIOUS,
    STRESSED,
    TIRED,
    NEUTRAL,
    ANGRY
}
