import { apiRequest } from './api';

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
  const response = await apiRequest<unknown>('/auth/exchange-session', {
    method: 'POST',
    data: {}
  });
  const result: unknown = response.data;
  if (typeof result !== 'object' || result === null || !('user' in result)) {
    throw new Error('The API returned an invalid sign-in response.');
  }
  return parseUserProfile(result.user);
}

export async function loadCurrentUser(): Promise<UserProfile> {
  const response = await apiRequest<unknown>('/auth/me');
  return parseUserProfile(response.data);
}

export async function logout(): Promise<void> {
  await apiRequest('/auth/logout', { method: 'POST' });
}
