import axios, { type AxiosRequestConfig } from 'axios';

export const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
export const API_V1 = `${API_ORIGIN}/api/v1`;

const CSRF_COOKIE = 'XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
let csrfInitialization: Promise<void> | null = null;

export class UnauthorizedApiError extends Error {
  constructor() {
    super('Your sign-in has expired. Please sign in again.');
    this.name = 'UnauthorizedApiError';
  }
}

export const apiClient = axios.create({
  baseURL: API_V1,
  withCredentials: true,
  xsrfCookieName: CSRF_COOKIE,
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: ({ method }) => !SAFE_METHODS.has((method ?? 'GET').toUpperCase())
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      return Promise.reject(new UnauthorizedApiError());
    }
    return Promise.reject(error);
  }
);

function hasCsrfCookie(): boolean {
  if (typeof document === 'undefined') {
    return false;
  }
  const prefix = `${CSRF_COOKIE}=`;
  return document.cookie.split(';').some((cookie) => {
    const entry = cookie.trimStart();
    return entry.startsWith(prefix) && entry.length > prefix.length;
  });
}

async function ensureCsrfCookie(): Promise<void> {
  if (hasCsrfCookie()) {
    return;
  }

  if (csrfInitialization === null) {
    const initialization = apiClient.get('/auth/csrf')
      .then(() => {
        if (!hasCsrfCookie()) {
          throw new Error('The CSRF cookie is unavailable to this frontend.');
        }
      })
      .catch((error: unknown) => {
        if (error instanceof UnauthorizedApiError) {
          throw error;
        }
        throw new Error('Unable to initialize secure sign-in.');
      });
    csrfInitialization = initialization;
  }

  const initialization = csrfInitialization;
  try {
    await initialization;
  } finally {
    if (csrfInitialization === initialization) {
      csrfInitialization = null;
    }
  }
}

export async function apiRequest<T = unknown>(
  path: string,
  config: AxiosRequestConfig = {}
) {
  const method = (config.method ?? 'GET').toUpperCase();
  if (!SAFE_METHODS.has(method)) {
    await ensureCsrfCookie();
  }
  return apiClient.request<T>({ ...config, url: path });
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const body: unknown = error.response?.data;
    if (typeof body === 'string' && body.trim() !== '') {
      return body;
    }
    if (typeof body === 'object' && body !== null) {
      const details = body as Record<string, unknown>;
      for (const key of ['message', 'detail', 'title']) {
        if (typeof details[key] === 'string') {
          return details[key];
        }
      }
    }
    return fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
