import { afterEach, describe, expect, it, vi } from 'vitest';
import { exchangeOAuthSession, loadCurrentUser, logout } from './auth';
import { API_V1, resetCsrfToken, UnauthorizedApiError } from './api';

afterEach(() => {
  resetCsrfToken();
  vi.unstubAllGlobals();
});

describe('cookie authentication flow', () => {
  it('exchanges OAuth state and returns only the safe user profile', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      .mockResolvedValueOnce(Response.json({
        user: { email: 'person@example.com', name: 'Person', provider: 'google' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(exchangeOAuthSession()).resolves.toEqual({
      email: 'person@example.com',
      name: 'Person',
      provider: 'google'
    });

    expect(fetchMock.mock.calls[1][0]).toBe(`${API_V1}/auth/exchange-session`);
    expect((fetchMock.mock.calls[1][1] as RequestInit).credentials).toBe('include');
  });

  it('treats a missing or expired cookie as signed out', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })));

    await expect(loadCurrentUser()).rejects.toBeInstanceOf(UnauthorizedApiError);
  });

  it('does not accept an exchange response without a user profile', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      .mockResolvedValueOnce(Response.json({ token: 'must-not-be-returned' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(exchangeOAuthSession()).rejects.toThrow('invalid sign-in response');
  });

  it('treats an expired OAuth exchange state as signed out', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(exchangeOAuthSession()).rejects.toBeInstanceOf(UnauthorizedApiError);
  });

  it('performs CSRF-protected logout with credentials', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(logout()).resolves.toBeUndefined();

    expect(fetchMock.mock.calls[1][0]).toBe(`${API_V1}/auth/logout`);
    expect(new Headers((fetchMock.mock.calls[1][1] as RequestInit).headers).get('X-XSRF-TOKEN'))
      .toBe('csrf');
  });

  it('obtains a fresh CSRF token after sign-in and sign-out', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      .mockResolvedValueOnce(Response.json({
        user: { email: 'person@example.com', name: 'Person', provider: 'google' }
      }))
      .mockResolvedValueOnce(Response.json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-next' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await exchangeOAuthSession();
    await logout();

    const csrfCalls = fetchMock.mock.calls.filter(([url]) => url === `${API_V1}/auth/csrf`);
    expect(csrfCalls).toHaveLength(2);
    expect(new Headers((fetchMock.mock.calls[3][1] as RequestInit).headers).get('X-XSRF-TOKEN'))
      .toBe('csrf-next');
  });
});
