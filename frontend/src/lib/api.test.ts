import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, API_V1, resetCsrfToken, UnauthorizedApiError } from './api';

afterEach(() => {
  resetCsrfToken();
  vi.unstubAllGlobals();
});

describe('apiFetch', () => {
  it('sends versioned API reads with cookies', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/events');

    expect(fetchMock).toHaveBeenCalledWith(`${API_V1}/events`, {
      method: 'GET',
      headers: expect.any(Headers),
      credentials: 'include'
    });
  });

  it('initializes CSRF and sends the token on unsafe requests', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-value' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/auth/logout', { method: 'POST' });

    expect(fetchMock).toHaveBeenNthCalledWith(1, `${API_V1}/auth/csrf`, { credentials: 'include' });
    const request = fetchMock.mock.calls[1][1] as RequestInit;
    expect(request.credentials).toBe('include');
    expect(new Headers(request.headers).get('X-XSRF-TOKEN')).toBe('csrf-value');
  });

  it('surfaces unauthorized responses so callers can clear signed-in state', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiFetch('/auth/me')).rejects.toBeInstanceOf(UnauthorizedApiError);
  });

  it('does not call the protected endpoint when CSRF initialization fails', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 403 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiFetch('/events/12/book', { method: 'POST' })).rejects.toThrow(
      'Unable to initialize secure sign-in.'
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reuses the cached CSRF token for later unsafe requests', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-value' }))
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/events/12/book', { method: 'POST' });
    await apiFetch('/auth/logout', { method: 'POST' });

    const csrfCalls = fetchMock.mock.calls.filter(([url]) => url === `${API_V1}/auth/csrf`);
    expect(csrfCalls).toHaveLength(1);
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('X-XSRF-TOKEN')).toBe('csrf-value');
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get('X-XSRF-TOKEN')).toBe('csrf-value');
  });

  it('shares one CSRF initialization between concurrent unsafe requests', async () => {
    const fetchMock = vi.fn((url: string) =>
      url === `${API_V1}/auth/csrf`
        ? Promise.resolve(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-value' }))
        : Promise.resolve(new Response(null, { status: 204 }))
    );
    vi.stubGlobal('fetch', fetchMock);

    await Promise.all([
      apiFetch('/events/12/book', { method: 'POST' }),
      apiFetch('/auth/logout', { method: 'POST' })
    ]);

    const csrfCalls = fetchMock.mock.calls.filter(([url]) => url === `${API_V1}/auth/csrf`);
    expect(csrfCalls).toHaveLength(1);
  });

  it('retries an unsafe request once with a fresh token after a 403', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'stale' }))
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'fresh' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await apiFetch('/events/12/book', { method: 'POST' });

    expect(response.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('X-XSRF-TOKEN')).toBe('stale');
    expect(new Headers(fetchMock.mock.calls[3][1].headers).get('X-XSRF-TOKEN')).toBe('fresh');
  });

  it('does not retry an unsafe request more than once', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'stale' }))
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'fresh' }))
      .mockResolvedValueOnce(new Response(null, { status: 403 }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await apiFetch('/events/12/book', { method: 'POST' });

    expect(response.status).toBe(403);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('does not replay safe requests when the response is a 403', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 403 }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await apiFetch('/events');

    expect(response.status).toBe(403);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('fetches a new token after the cached one is reset', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'first' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'second' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/auth/logout', { method: 'POST' });
    resetCsrfToken();
    await apiFetch('/auth/logout', { method: 'POST' });

    const csrfCalls = fetchMock.mock.calls.filter(([url]) => url === `${API_V1}/auth/csrf`);
    expect(csrfCalls).toHaveLength(2);
    expect(new Headers(fetchMock.mock.calls[3][1].headers).get('X-XSRF-TOKEN')).toBe('second');
  });

  it('recovers from a failed CSRF initialization on the next request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 500 }))
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-value' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiFetch('/events/12/book', { method: 'POST' })).rejects.toThrow(
      'Unable to initialize secure sign-in.'
    );
    const recovered = await apiFetch('/events/12/book', { method: 'POST' });

    expect(recovered.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
