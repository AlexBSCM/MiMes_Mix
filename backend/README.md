# MiMes Backend

Express + Prisma (SQLite) + Socket.IO сервер для мессенджера MiMes.

## Запуск

```bash
# 1. Установка зависимостей
npm install

# 2. Конфигурация (создать .env)
cp .env.example .env   # или вручную

# 3. Создать базу и применить миграции
npx prisma migrate dev

# 4. Запуск
npm run dev    # разработка (nodemon)
npm start      # продакшен
```

## Переменные окружения (`.env`)

| Переменная | Значение по умолчанию | Описание |
|-----------|----------------------|----------|
| `DATABASE_URL` | `file:./dev.db` | Путь к SQLite-базе |
| `JWT_SECRET` | `mi_mes_super_secret_change_me` | Секрет для подписи JWT |
| `PORT` | `3000` | Порт HTTP-сервера |

## REST API

### Auth
| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/auth/register` | Регистрация `{username, password}` → `{token, user}` |
| `POST` | `/api/auth/login` | Вход → `{token, user}` |
| `GET` | `/api/auth/me` | Текущий пользователь (JWT) |

### Диалоги (требуют `Authorization: Bearer <token>`)
| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/conversations` | Создать диалог `{participantIds[], name?, isGroup?}` |
| `GET` | `/api/conversations` | Список диалогов с последним сообщением |
| `GET` | `/api/conversations/:id` | Детали диалога |

### Сообщения
| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/conversations/:id/messages` | Сообщения (50 шт, пагинация через `?cursor=`) |
| `POST` | `/api/conversations/:id/messages` | Отправить `{content?, mediaUrl?}` → push по Socket.IO |

### Здоровье
| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/health` | Статус сервера |

## Socket.IO

Подключение: `ws://localhost:3000?token=<JWT>`

| Событие (клиент→сервер) | Описание |
|------------------------|----------|
| `typing:start` / `typing:stop` | Индикатор «печатает…» (`conversationId`) |

| Событие (сервер→клиент) | Описание |
|------------------------|----------|
| `message:new` | Новое сообщение в диалоге |
| `typing:start` / `typing:stop` | Собеседник печатает |

При подключении клиент автоматически присоединяется к комнатам своих диалогов, поэтому `message:new` доставляется только участникам.
