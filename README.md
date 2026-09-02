# MiMes Mix

Современный мессенджер для Android с поддержкой чатов, аудио/видеозвонков и push-уведомлений.

[![Android CI](https://github.com/AlexBSCM/MiMes_Mix/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AlexBSCM/MiMes_Mix/actions/workflows/android-ci.yml)

## Стек технологий

| Компонент | Технологии |
|-----------|-----------|
| **Android** | Kotlin, Jetpack Compose, Hilt (DI), MVVM, Navigation Compose |
| **Backend** | Node.js, Express, Prisma (SQLite) — ⬜ в разработке |
| **Frontend** | Expo / React Native — ⬜ в разработке |
| **Push** | Firebase Cloud Messaging + Cloud Functions |
| **Звонки** | WebRTC (stream-webrtc-android), сигналинг через Firestore |
| **Хранилище** | Firebase Firestore (чат, звонки), Firebase Storage (файлы/аватары) |

## Текущий статус (Android)

| Этап | Статус |
|------|--------|
| 1. Основа проекта (Compose, Hilt, навигация, тема) | ✅ |
| 2. Авторизация (вход/регистрация, сессия) | ✅ |
| 3. Список чатов (realtime, поиск, непрочитанные) | ✅ |
| 4. Переписка (текст, файлы, статусы) | ✅ |
| 5. Передача файлов (Storage, preview) | ✅ |
| 6. Аудио/Видеозвонки (WebRTC, foreground service) | ✅ |
| 7. Push-уведомления (FCM, каналы, deep-linking) | ✅ |
| 8. Профиль и настройки (аватар, никнейм, уведомления) | ✅ |
| 9. Полировка (загрузка, ошибки, анимации, ProGuard) | ✅ |

## Сборка Android-приложения

### Требования
- Android Studio Ladybug (2024.2+) или JDK 17+
- Android SDK 34 (platforms;android-34)

### Шаг 1: Firebase-конфигурация
1. Открой [Firebase Console](https://console.firebase.google.com/), выбери проект `mimes-f9a2d` (или создай новый)
2. Зарегистрируй Android-приложение с **package name** `com.mimes.app`
3. Скачай `google-services.json` и положи в `app/google-services.json`

### Шаг 2: Сборка
```bash
# Отладка
./gradlew assembleDebug

# Релиз (с R8 — может потребоваться >4 ГБ свободной памяти)
./gradlew assembleRelease
```

Готовый APK: `app/build/outputs/apk/debug/app-debug.apk`

### Шаг 3: Push-уведомления (Cloud Functions)
```bash
cd functions
npm install
firebase deploy --only functions
```

Функции автоматически отправляют push при:
- Новом сообщении в `chats/{chatId}/messages/{messageId}`
- Входящем звонке (`calls/{callId}` со статусом `ringing`)

## Структура проекта (Android)

```
app/src/main/java/com/mimes/app/
├── MiMesApp.kt          # Application + каналы уведомлений
├── MainActivity.kt      # Точка входа, deep-linking
├── data/                # Message.kt, DataInitializer.kt
├── rtc/                 # WebRTC: RtcManager, CallViewModel, CallScreen
├── service/             # FCMService, CallService, CallActionReceiver
└── ui/
    ├── auth/            # Экран входа
    ├── chat/            # Список чатов
    ├── chatdetail/      # Экран переписки
    ├── navigation/      # Навигация (NavHost)
    ├── profile/         # Профиль и настройки
    └── theme/           # Тема (Material3, Dynamic Color)
```

## Лицензия

MIT