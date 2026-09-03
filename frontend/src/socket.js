import io from 'socket.io-client';
import { API_URL } from './config';
import { getToken } from './api/client';

let socket = null;

/** Возвращает подключённый socket (auth через token). */
export function connectSocket() {
  if (socket) return socket;
  socket = io(API_URL, { query: { token: '' } });

  // Подключаемся с токеном после того, как получим его
  getToken().then((token) => {
    if (token) {
      socket.auth = { token };
      socket.disconnect();
      socket = io(API_URL, { auth: { token } });
    }
  });

  socket.on('connect', () => console.log('Socket connected'));
  socket.on('connect_error', (err) => console.warn('Socket error:', err.message));
  return socket;
}

export function disconnectSocket() {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
}

export function getSocket() {
  return socket;
}
