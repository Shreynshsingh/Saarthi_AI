"use client";

import { AuthResponse } from "./types";

/**
 * REST client for the Java backend. Native fetch only.
 * Token storage per 12_SECURITY_AND_AUTHENTICATION.md section 14:
 * localStorage for the hackathon MVP (production V2: httpOnly cookie).
 */

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";
const TOKEN_KEY = "saarthi_token";
const NAME_KEY = "saarthi_name";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(NAME_KEY);
}

export function getUserName(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(NAME_KEY);
}

export function saveSession(token: string, name: string): void {
  setToken(token);
  window.localStorage.setItem(NAME_KEY, name);
}

export function isLoggedIn(): boolean {
  return getToken() !== null;
}

interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T | null;
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

/**
 * Authenticated fetch against ${NEXT_PUBLIC_API_URL}. Any 401 clears the
 * session and — unless already on an auth page — redirects to /login so an
 * expired token never strands the user on a dead screen.
 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  opts: { json?: boolean } = {}
): Promise<T> {
  let res: Response;
  try {
    const headers = new Headers(options.headers);
    if (opts.json !== false) headers.set("Content-Type", "application/json");
    const token = getToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    res = await fetch(`${API_URL}${path}`, { ...options, headers });
  } catch {
    throw new ApiError(0, "I can't reach Saarthi right now. Please check your connection and try again.");
  }
  if (res.status === 401) {
    clearToken();
    if (typeof window !== "undefined") {
      const here = window.location.pathname;
      if (here !== "/login" && here !== "/register") window.location.href = "/login";
    }
    throw new ApiError(401, "Your session has expired. Please log in again.");
  }
  let body: ApiEnvelope<T>;
  try {
    body = (await res.json()) as ApiEnvelope<T>;
  } catch {
    throw new ApiError(res.status, "Something went wrong on our side. Please try again.");
  }
  if (!res.ok || !body.success) {
    throw new ApiError(res.status, body.message || "Something went wrong. Please try again.");
  }
  return body.data as T;
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function register(
  name: string,
  email: string,
  password: string,
  preferredLanguage?: string
): Promise<unknown> {
  return apiFetch<unknown>("/auth/register", {
    method: "POST",
    body: JSON.stringify({ name, email, password, preferredLanguage: preferredLanguage ?? null }),
  });
}
