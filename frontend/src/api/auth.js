import { api } from './client';

export function register(username, password) {
  return api('/api/auth/register', { method: 'POST', body: { username, password } });
}

export function login(username, password) {
  return api('/api/auth/login', { method: 'POST', body: { username, password } });
}

export function fetchMe() {
  return api('/api/auth/me');
}
