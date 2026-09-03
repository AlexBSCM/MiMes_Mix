import { api } from './client';

/** Список диалогов пользователя. */
export function fetchConversations() {
  return api('/api/conversations');
}

/** Создание диалога: participantIds — id остальных участников. */
export function createConversation(participantIds, { name, isGroup } = {}) {
  return api('/api/conversations', {
    method: 'POST',
    body: { participantIds, name, isGroup },
  });
}

/** Сообщения диалога. */
export function fetchMessages(conversationId) {
  return api(`/api/conversations/${conversationId}/messages`);
}

/** Отправка сообщения. */
export function sendMessage(conversationId, content) {
  return api(`/api/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: { content },
  });
}
