export const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
export const API_V1 = `${API_ORIGIN}/api/v1`;

export class UnauthorizedApiError extends Error {
  constructor() {
    super('Your sign-in has expired. Please sign in again.');
    this.name = 'UnauthorizedApiError';
  }
}

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

type CsrfResponse = {
  headerName: string;
  token: string;
};

let csrfToken: Promise<CsrfResponse> | null = null;

async function loadCsrfToken(): Promise<CsrfResponse> {
  const response = await fetch(`${API_V1}/auth/csrf`, { credentials: 'include' });
  if (response.status === 401) {
    throw new UnauthorizedApiError();
  }
  if (!response.ok) {
    throw new Error('Unable to initialize secure sign-in.');
  }

  const csrf: unknown = await response.json();
  if (
    typeof csrf !== 'object' ||
    csrf === null ||
    !('headerName' in csrf) ||
    typeof csrf.headerName !== 'string' ||
    !('token' in csrf) ||
    typeof csrf.token !== 'string'
  ) {
    throw new Error('The API returned an invalid CSRF token response.');
  }
  return { headerName: csrf.headerName, token: csrf.token };
}

function getCsrfToken(): Promise<CsrfResponse> {
  csrfToken ??= loadCsrfToken().catch((error) => {
    csrfToken = null;
    throw error;
  });
  return csrfToken;
}

export function resetCsrfToken(): void {
  csrfToken = null;
}

async function send(
  path: string,
  method: string,
  init: RequestInit,
  csrf: CsrfResponse | null
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (csrf !== null) {
    headers.set(csrf.headerName, csrf.token);
  }
  return fetch(`${API_V1}${path.startsWith('/') ? path : `/${path}`}`, {
    ...init,
    method,
    headers,
    credentials: 'include'
  });
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const unsafe = !SAFE_METHODS.includes(method);

  let response = await send(path, method, init, unsafe ? await getCsrfToken() : null);
  if (unsafe && response.status === 403) {
    // A rejected request changes nothing, so it is safe to replay once with a
    // freshly issued token in case the server rotated the token.
    resetCsrfToken();
    response = await send(path, method, init, await getCsrfToken());
  }

  if (response.status === 401) {
    throw new UnauthorizedApiError();
  }
  return response;
}
