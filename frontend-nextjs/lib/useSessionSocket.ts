"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChatRequest, ChatResponse } from "./types";
import { SaarthiWebSocket, SocketStatus } from "./websocket";

export type ConnectionState = "connecting" | "open" | "reconnecting" | "closed" | "unauthorized";

/**
 * React binding over SaarthiWebSocket: stable callbacks, reconnectable
 * connection, and calm human messaging for every failure mode.
 */
export function useSessionSocket(token: string | null, onFrame: (frame: ChatResponse) => void) {
  const [state, setState] = useState<ConnectionState>("closed");
  const [notice, setNotice] = useState<string | null>(null);
  const socketRef = useRef<SaarthiWebSocket | null>(null);
  if (!socketRef.current) socketRef.current = new SaarthiWebSocket();
  const onFrameRef = useRef(onFrame);
  onFrameRef.current = onFrame;
  const everOpenedRef = useRef(false);

  const connect = useCallback(() => {
    if (!token) return;
    const socket = socketRef.current;
    if (!socket) return;
    setState(everOpenedRef.current ? "reconnecting" : "connecting");
    setNotice(null);
    socket.connect(
      token,
      (frame) => onFrameRef.current(frame),
      (status: SocketStatus) => {
        if (status === "connected") {
          everOpenedRef.current = true;
          setState("open");
          setNotice(null);
        } else if (status === "error") {
          if (!everOpenedRef.current) {
            setNotice("I can't reach Saarthi right now. Is the backend running? Press Reconnect to try again.");
          }
        } else if (status === "disconnected") {
          if (socket.wasIntentional()) {
            setState("closed");
          } else if (socket.closeCode === 4401) {
            setState("unauthorized");
            setNotice("Your session has expired. Please log in again to keep talking.");
          } else {
            setState("closed");
            setNotice(
              everOpenedRef.current
                ? "Connection lost. Take your time — press Reconnect when you're ready."
                : "I can't reach Saarthi right now. Is the backend running? Press Reconnect to try again."
            );
          }
        }
      }
    );
  }, [token]);

  const disconnect = useCallback(() => {
    socketRef.current?.disconnect();
    setState("closed");
  }, []);

  const send = useCallback((message: ChatRequest): boolean => {
    return socketRef.current?.send(message) ?? false;
  }, []);

  useEffect(() => {
    connect();
    return () => {
      socketRef.current?.disconnect();
    };
  }, [connect]);

  return { state, notice, send, connect, disconnect };
}
