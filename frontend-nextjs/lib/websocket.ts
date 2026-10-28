"use client";

import { ChatRequest, ChatResponse, parseChatResponse } from "./types";

export type SocketStatus = "connecting" | "connected" | "disconnected" | "error";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws/mentor";

/**
 * One socket per live session. Browser WebSocket auth travels as a query
 * param because the API cannot set headers; the backend closes unauthenticated
 * handshakes with 4401, exposed here as closeCode for the caller to interpret.
 */
export class SaarthiWebSocket {
  private ws: WebSocket | null = null;
  private intentionalClose = false;
  private status: SocketStatus = "disconnected";
  closeCode: number | null = null;

  get currentStatus(): SocketStatus {
    return this.status;
  }

  get isOpen(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  connect(
    token: string,
    onMessage: (frame: ChatResponse) => void,
    onStatusChange: (status: SocketStatus) => void
  ): void {
    this.intentionalClose = false;
    this.closeCode = null;
    this.closeSocket();
    this.setStatus("connecting", onStatusChange);

    const ws = new WebSocket(`${WS_URL}?token=${encodeURIComponent(token)}`);
    this.ws = ws;

    ws.onopen = () => {
      this.setStatus("connected", onStatusChange);
    };
    ws.onmessage = (event: MessageEvent) => {
      const frame = parseChatResponse(String(event.data));
      if (frame) onMessage(frame);
    };
    ws.onerror = () => {
      // onclose always follows; surfaced so the UI can react immediately.
      this.setStatus("error", onStatusChange);
    };
    ws.onclose = (event: CloseEvent) => {
      this.ws = null;
      this.closeCode = event.code;
      this.setStatus("disconnected", onStatusChange);
    };
  }

  send(message: ChatRequest): boolean {
    if (!this.isOpen || !this.ws) return false;
    this.ws.send(JSON.stringify(message));
    return true;
  }

  disconnect(): void {
    this.intentionalClose = true;
    this.closeSocket();
    this.setStatus("disconnected", () => undefined);
  }

  wasIntentional(): boolean {
    return this.intentionalClose;
  }

  private closeSocket(): void {
    try {
      this.ws?.close();
    } catch {
      // already gone
    }
    this.ws = null;
  }

  private setStatus(status: SocketStatus, onStatusChange: (status: SocketStatus) => void): void {
    this.status = status;
    onStatusChange(status);
  }
}
