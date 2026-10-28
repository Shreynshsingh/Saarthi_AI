"use client";

/**
 * Ordered MP3 queue. Mentor sentences must play strictly in arrival order,
 * so there is exactly one <audio> pipeline: each chunk starts when the
 * previous one ends. Text-only chunks resolve immediately.
 */

export interface QueuedChunk {
  id: number;
  audioBase64: string | null;
}

export class AudioPlaybackQueue {
  private queue: QueuedChunk[] = [];
  private playing = false;
  private stopped = false;
  private current: HTMLAudioElement | null = null;
  private nextId = 1;
  private listeners = new Set<(speaking: boolean) => void>();

  onSpeakingChange(listener: (speaking: boolean) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(): void {
    const speaking = this.playing && !this.stopped;
    this.listeners.forEach((listener) => listener(speaking));
  }

  get isSpeaking(): boolean {
    return this.playing && !this.stopped;
  }

  enqueue(audioBase64: string | null | undefined): void {
    this.queue.push({ id: this.nextId++, audioBase64: audioBase64 ?? null });
    void this.pump();
  }

  /** Stop everything: used on End Session and crisis alerts. */
  stop(): void {
    this.stopped = true;
    this.queue = [];
    if (this.current) {
      this.current.pause();
      this.current.src = "";
      this.current = null;
    }
    this.playing = false;
    this.emit();
  }

  /** Re-arm after stop() so a fresh turn can play. */
  reset(): void {
    this.stopped = false;
  }

  private async pump(): Promise<void> {
    if (this.playing || this.stopped) return;
    const head = this.queue.shift();
    if (!head) return;
    if (!head.audioBase64) {
      void this.pump();
      return;
    }
    this.playing = true;
    this.emit();
    try {
      await this.playBase64(head.audioBase64);
    } catch {
      // A single bad chunk never breaks the turn; the subtitle already shows it.
    } finally {
      this.playing = false;
      this.emit();
      if (!this.stopped) void this.pump();
    }
  }

  private playBase64(base64: string): Promise<void> {
    return new Promise((resolve, reject) => {
      let url: string;
      try {
        const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
        url = URL.createObjectURL(new Blob([bytes], { type: "audio/mpeg" }));
      } catch {
        reject(new Error("bad audio"));
        return;
      }
      const audio = new Audio(url);
      this.current = audio;
      const done = () => {
        URL.revokeObjectURL(url);
        if (this.current === audio) this.current = null;
        resolve();
      };
      audio.onended = done;
      audio.onerror = () => {
        URL.revokeObjectURL(url);
        if (this.current === audio) this.current = null;
        reject(new Error("playback failed"));
      };
      void audio.play().catch(() => {
        URL.revokeObjectURL(url);
        if (this.current === audio) this.current = null;
        reject(new Error("play blocked"));
      });
    });
  }
}
