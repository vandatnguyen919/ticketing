import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { exchangeOAuthSession, loadCurrentUser, logout } from './auth';
import { apiClient, API_V1, UnauthorizedApiError } from './api';

type StubResponse = {
  status: number;
  data: unknown;
  afterResponse?: () => void;
};

const originalAdapter = apiClient.defaults.adapter;
let responses: StubResponse[];
let requests: InternalAxiosRequestConfig[];

const testAdapter: AxiosAdapter = async (config) => {
  requests.push(config);
  const stub = responses.shift();
  if (!stub) {
    throw new Error(`No response queued for ${config.method?.toUpperCase()} ${config.url}`);
  }

  const response: AxiosResponse = {
    data: stub.data,
    status: stub.status,
    statusText: String(stub.status),
    headers: new AxiosHeaders(),
    config
  };
  stub.afterResponse?.();
  if (stub.status < 200 || stub.status >= 300) {
    throw new AxiosError(`Request failed with status code ${stub.status}`, undefined, config, undefined, response);
  }
  return response;
};

beforeEach(() => {
  responses = [];
  requests = [];
  vi.stubGlobal('document', { cookie: '' });
  apiClient.defaults.adapter = testAdapter;
});

afterEach(() => {
  apiClient.defaults.adapter = originalAdapter;
  vi.unstubAllGlobals();
});

describe('cookie authentication flow', () => {
  it('bootstraps CSRF then exchanges OAuth state and returns only the safe profile', async () => {
    responses.push(
      { status: 204, data: null, afterResponse: () => { document.cookie = 'XSRF-TOKEN=csrf'; } },
      {
        status: 200,
        data: { user: { email: 'person@example.com', name: 'Person', provider: 'google' } },
        afterResponse: () => { document.cookie = ''; }
      }
    );

    await expect(exchangeOAuthSession()).resolves.toEqual({
      email: 'person@example.com',
      name: 'Person',
      provider: 'google'
    });

    expect(requests.map(({ url }) => url)).toEqual(['/auth/csrf', '/auth/exchange-session']);
    expect(requests[1].method).toBe('post');
    expect(requests[1].data).toBe('{}');
    expect(requests[1].withCredentials).toBe(true);
    expect(API_V1).toBe(requests[1].baseURL);
  });

  it('bootstraps a fresh cookie for logout after the prior cookie is cleared', async () => {
    responses.push(
      { status: 204, data: null, afterResponse: () => { document.cookie = 'XSRF-TOKEN=csrf-next'; } },
      { status: 204, data: null }
    );

    await expect(logout()).resolves.toBeUndefined();

    expect(requests.map(({ url }) => url)).toEqual(['/auth/csrf', '/auth/logout']);
    expect(requests[1].method).toBe('post');
    expect(requests[1].withCredentials).toBe(true);
  });

  it('loads the current user and maps unauthorized responses to signed-out state', async () => {
    document.cookie = 'XSRF-TOKEN=csrf';
    responses.push({ status: 401, data: null });

    await expect(loadCurrentUser()).rejects.toBeInstanceOf(UnauthorizedApiError);
    expect(requests[0].url).toBe('/auth/me');
  });

  it('rejects an OAuth exchange response without a user profile', async () => {
    document.cookie = 'XSRF-TOKEN=csrf';
    responses.push({ status: 200, data: { token: 'must-not-be-returned' } });

    await expect(exchangeOAuthSession()).rejects.toThrow('invalid sign-in response');
  });
});