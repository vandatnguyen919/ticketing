import { apiFetch, resetCsrfToken } from './api';

export type UserProfile = {
  email: string;
  name: string;
  provider: string;
};

function parseUserProfile(value: unknown): UserProfile {
  if (
    typeof value !== 'object' ||
    value === null ||
    !('email' in value) ||
    typeof value.email !== 'string' ||
    !('name' in value) ||
    typeof value.name !== 'string' ||
    !('provider' in value) ||
    typeof value.provider !== 'string'
  ) {
    throw new Error('The API returned an invalid user profile.');
  }
  return { email: value.email, name: value.name, provider: value.provider };
}

export async function exchangeOAuthSession(): Promise<UserProfile> {
  const response = await apiFetch('/auth/exchange-session', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}'
  });
  if (!response.ok) {
    throw new Error('Unable to complete provider sign-in.');
  }
  resetCsrfToken();
  const result: unknown = await response.json();
  if (typeof result !== 'object' || result === null || !('user' in result)) {
    throw new Error('The API returned an invalid sign-in response.');
  }
  return parseUserProfile(result.user);
}

export async function loadCurrentUser(): Promise<UserProfile> {
  const response = await apiFetch('/auth/me');
  if (!response.ok) {
    throw new Error('Unable to load your signed-in profile.');
  }
  return parseUserProfile(await response.json());
}

export async function logout(): Promise<void> {
  const response = await apiFetch('/auth/logout', { method: 'POST' });
  if (!response.ok) {
    throw new Error('Unable to log out. Please try again.');
  }
  resetCsrfToken();
}
