import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient, apiRequest, API_V1, UnauthorizedApiError } from './api';

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

function xsrfEnabled(method: string): boolean {
  const setting = apiClient.defaults.withXSRFToken;
  if (typeof setting !== 'function') {
    throw new Error('Axios XSRF method predicate is not configured.');
  }
  return setting({ method } as InternalAxiosRequestConfig) ?? false;
}

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

describe('Axios API client', () => {
  it('sends versioned reads with credentials and does not bootstrap when the cookie exists', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value';
    responses.push({ status: 200, data: [] });

    await apiRequest('/events');

    expect(requests).toHaveLength(1);
    expect(requests[0].baseURL).toBe(API_V1);
    expect(requests[0].url).toBe('/events');
    expect(requests[0].method).toBe('get');
    expect(requests[0].withCredentials).toBe(true);
    expect(apiClient.defaults.xsrfCookieName).toBe('XSRF-TOKEN');
    expect(apiClient.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN');
    expect(xsrfEnabled('get')).toBe(false);
  });

  it('initializes a bodyless CSRF cookie before an unsafe request', async () => {
    responses.push(
      {
        status: 204,
        data: { token: 'must-be-ignored' },
        afterResponse: () => { document.cookie = 'XSRF-TOKEN=raw-cookie-value'; }
      },
      { status: 204, data: null }
    );

    await apiRequest('/auth/logout', { method: 'POST' });

    expect(requests.map(({ url }) => url)).toEqual(['/auth/csrf', '/auth/logout']);
    expect(requests.every(({ withCredentials }) => withCredentials)).toBe(true);
    expect(xsrfEnabled('post')).toBe(true);
    expect(xsrfEnabled('put')).toBe(true);
    expect(xsrfEnabled('delete')).toBe(true);
    expect(xsrfEnabled('options')).toBe(false);
  });

  it('shares one CSRF bootstrap between concurrent unsafe requests', async () => {
    responses.push(
      { status: 204, data: null, afterResponse: () => { document.cookie = 'XSRF-TOKEN=raw-cookie-value'; } },
      { status: 204, data: null },
      { status: 204, data: null }
    );

    await Promise.all([
      apiRequest('/events/12/book', { method: 'POST' }),
      apiRequest('/auth/logout', { method: 'POST' })
    ]);

    expect(requests.filter(({ url }) => url === '/auth/csrf')).toHaveLength(1);
  });

  it('translates unauthorized responses for callers', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value';
    responses.push({ status: 401, data: null });

    await expect(apiRequest('/auth/me')).rejects.toBeInstanceOf(UnauthorizedApiError);
  });

  it('does not send an unsafe request if CSRF initialization fails', async () => {
    responses.push({ status: 500, data: null });

    await expect(apiRequest('/events/12/book', { method: 'POST' })).rejects.toThrow(
      'Unable to initialize secure sign-in.'
    );
    expect(requests.map(({ url }) => url)).toEqual(['/auth/csrf']);
  });

  it('does not send an unsafe request if the bootstrap cookie is not readable', async () => {
    responses.push({ status: 204, data: null });

    await expect(apiRequest('/events/12/book', { method: 'POST' })).rejects.toThrow(
      'Unable to initialize secure sign-in.'
    );
    expect(requests.map(({ url }) => url)).toEqual(['/auth/csrf']);
  });

  it('does not automatically replay a modifying request after a 403', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value';
    responses.push({ status: 403, data: null });

    await expect(apiRequest('/events/12/book', { method: 'POST' })).rejects.toBeInstanceOf(AxiosError);
    expect(requests.map(({ url }) => url)).toEqual(['/events/12/book']);
  });
});