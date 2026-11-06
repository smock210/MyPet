# Auth API — сервис аутентификации для Android

Базовый URL: `https://www.auth.lemniscatasaa.ru`

Health-check: `GET /` → `{"status": "ok", "service": "authApi", "endpoints": [...]}`

Все запросы и ответы — JSON (`Content-Type: application/json`, UTF-8).
Защищённые запросы — заголовок `Authorization: Bearer <access>`.

## Общие принципы

1. Android-приложение использует сторонних провайдеров (Google, VK) и/или email + пароль.
2. Клиент отправляет провайдерский токен (или email/пароль) в auth-сервис.
3. Сервис валидирует данные, создаёт/находит пользователя, выдаёт собственный JWT.
4. Android хранит `access`/`refresh` и использует `access` для защищённых запросов.

## Эндпоинты

| Метод | Путь | Авторизация | Описание |
|---|---|---|---|
| POST | `/authApi/register/` | — | Регистрация по email + паролю, сразу выдаёт JWT |
| POST | `/authApi/login/` | — | Вход по email + паролю |
| POST | `/authApi/login/google/` | — | Вход через Google (ID token) |
| POST | `/authApi/login/vk/` | — | Вход через VK (access token) |
| POST | `/authApi/token/refresh/` | — | Обновление `access` по `refresh` |
| POST | `/authApi/logout/` | — | Отзыв `refresh` (blacklist) |
| GET | `/authApi/me/` | Bearer | Текущий пользователь |
| POST | `/authApi/password/change/` | Bearer | Смена пароля (знает старый) |
| POST | `/authApi/password/reset/` | — | Запрос кода сброса на email |
| POST | `/authApi/password/reset/confirm/` | — | Установка нового пароля по коду |

## Формат ошибок

Все ошибки — `{"error": "<человекочитаемое сообщение>"}`.

При ошибках валидации дополнительно приходит `details` с разбивкой по полям:

```json
{
  "error": "This password is too short. It must contain at least 8 characters.",
  "details": {"password": ["This password is too short. It must contain at least 8 characters."]}
}
```

Ошибки троттлинга отдаёт DRF в своём формате:

```json
{"detail": "Request was throttled. Expected available in 42 seconds."}
```

---

## POST /authApi/register/

**Запрос:**

```json
{
  "email": "user@example.com",
  "password": "secret-password"
}
```

**Ответ `201 Created`:**

```json
{
  "message": "User registered successfully",
  "refresh": "<refresh-jwt>",
  "access": "<access-jwt>",
  "user": {"id": 1, "username": "user@example.com", "email": "user@example.com"}
}
```

Токены выдаются сразу — отдельный вызов `/login/` не нужен.

**Валидация:**

| Правило | Ошибка |
|---|---|
| email обязателен и должен быть валидным | `Enter a valid email address.` |
| email приводится к нижнему регистру, пробелы обрезаются | — |
| email/username уже занят | `User already exists` |
| пароль обязателен, минимум 8 символов, не слишком простой | `This password is too short...` / `This password is too common.` |

**Ошибки:** `400` — см. выше.

## POST /authApi/login/

**Запрос:**

```json
{
  "email": "user@example.com",
  "password": "secret-password"
}
```

**Ответ `200 OK`:**

```json
{
  "refresh": "<refresh-jwt>",
  "access": "<access-jwt>"
}
```

Поиск пользователя идёт по email (регистр не важен), поэтому вход работает и для
соц-аккаунтов, у которых `username != email`.

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"error": "Email and password are required"}` |
| `400` | `{"error": "Enter a valid email address."}` |
| `401` | `{"error": "Invalid credentials"}` (неверный пароль или нет пользователя) |
| `429` | троттлинг (10 запросов в минуту на IP) |

## POST /authApi/login/google/

**Запрос (основной путь — Google Sign-In ID token):**

```json
{
  "id_token": "<Google ID token>"
}
```

**Запрос (fallback — пользователь уже сохранён в связке):**

```json
{
  "provider_user_id": "1234567890"
}
```

**Ответ `200 OK`:**

```json
{
  "refresh": "<refresh-jwt>",
  "access": "<access-jwt>",
  "user": {
    "id": 1,
    "username": "user@example.com",
    "email": "user@example.com"
  }
}
```

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"error": "Either id_token or provider_user_id are required"}` |
| `401` | `{"error": "Invalid Google token"}` |
| `401` | `{"error": "Failed to resolve Google user"}` |
| `404` | `{"error": "User not found"}` (fallback по `provider_user_id`) |

> Сервер проверяет `aud` ID token'а = `GOOGLE_CLIENT_ID` из `settings.py`.
> Это должен быть **Web Client ID**, а не Android Client ID.

## POST /authApi/login/vk/

**Запрос:**

```json
{
  "access_token": "<VK access token>"
}
```

**Ответ `200 OK`:** как у Google (`refresh`, `access`, `user`).

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"error": "access_token is required"}` |
| `401` | `{"error": "Invalid VK token"}` |
| `401` | `{"error": "Failed to resolve VK user"}` |

Сервер сам валидирует токен через `https://api.vk.com/method/users.get` (v=5.131).

## POST /authApi/token/refresh/

**Запрос:**

```json
{
  "refresh": "<refresh-jwt>"
}
```

**Ответ `200 OK`:**

```json
{
  "access": "<new access-jwt>"
}
```

Refresh-токен **не ротируется**: нового `refresh` в ответе нет.

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"refresh": ["This field is required."]}` |
| `401` | `{"detail": "Token is blacklisted", "code": "token_not_valid"}` |

## POST /authApi/logout/

**Запрос:**

```json
{
  "refresh": "<refresh-jwt>"
}
```

**Ответ `200 OK`:** `{"detail": "Successfully logged out"}`

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"error": "Refresh token is required"}` |
| `401` | `{"error": "Invalid refresh token"}` |

После logout сервер отзывает только `refresh`. Клиент обязан сам удалить оба токена.

## GET /authApi/me/

**Заголовок:** `Authorization: Bearer <access-jwt>`

**Ответ `200 OK`:**

```json
{"id": 1, "username": "...", "email": "...", "first_name": "...", "last_name": "..."}
```

**Ошибки `401`:**

```json
{"detail": "Authentication credentials were not provided.", "code": "not_authenticated"}
{"detail": "Given token not valid for any token type", "code": "token_not_valid"}
```

---

## POST /authApi/password/change/

Смена пароля для авторизованного пользователя.

**Заголовок:** `Authorization: Bearer <access-jwt>`

**Запрос:**

```json
{
  "old_password": "secret-password",
  "new_password": "new-secret-password"
}
```

**Ответ `200 OK`:**

```json
{"message": "Password changed successfully. Please sign in again."}
```

После успешной смены **все refresh-токены пользователя отзываются** (blacklist),
поэтому приложение должно разлогинить пользователя и попросить войти заново.

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"error": "old_password and new_password are required"}` (поля отсутствуют) |
| `400` | `{"error": "Old password is incorrect"}` |
| `400` | `{"error": "New password must be different from the old one"}` |
| `400` | `{"error": "Password does not meet requirements", "details": [...]}` |
| `400` | `{"error": "This account has no password. Use password reset to set one."}` |
| `401` | нет/просрочен access |
| `429` | троттлинг (10 в минуту) |

## POST /authApi/password/reset/

Запрос одноразового кода сброса. Ответ **всегда `200`**, чтобы нельзя было
проверить существование email.

**Запрос:**

```json
{
  "email": "user@example.com"
}
```

**Ответ `200 OK`:**

```json
{"message": "If this email exists, a reset code has been sent"}
```

Если `DEBUG = True` (сейчас на сервере включён), в ответ добавляется код —
удобно для отладки:

```json
{"message": "If this email exists, a reset code has been sent", "debug_code": "482913"}
```

**Параметры кода:**

| Параметр | Значение | Настройка |
|---|---|---|
| Длина | 6 цифр | — |
| Время жизни | 15 минут | `PASSWORD_RESET_CODE_TTL_MINUTES` |
| Попыток ввода | 5 на код | `PASSWORD_RESET_MAX_ATTEMPTS` |
| Хранение | только хеш (`make_password`) | — |
| Повторный запрос | инвалидирует предыдущие коды | — |

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"error": "Enter a valid email address."}` |
| `429` | троттлинг (5 в час на IP) |

> На сервере по умолчанию `EMAIL_BACKEND = console` — письма **не отправляются**,
> а печатаются в лог. Для реальной отправки задайте переменные окружения
> `EMAIL_BACKEND`, `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_HOST_USER`,
> `EMAIL_HOST_PASSWORD`, `EMAIL_USE_TLS`, `DEFAULT_FROM_EMAIL`.

## POST /authApi/password/reset/confirm/

Установка нового пароля по коду из письма.

**Запрос:**

```json
{
  "email": "user@example.com",
  "code": "482913",
  "new_password": "new-secret-password"
}
```

**Ответ `200 OK`:**

```json
{"message": "Password has been reset successfully"}
```

**Ошибки:**

| Код | Тело |
|---|---|
| `400` | `{"error": "Invalid or expired code"}` (неверный/просроченный/использованный код) |
| `400` | `{"error": "Password does not meet requirements", "details": [...]}` |
| `429` | троттлинг (20 в час на IP) |

После успешного сброса все refresh-токены пользователя отзываются; вход — с новым паролем.
Слабый пароль проверяется **до** кода, поэтому код не «сгорает» впустую.

---

## Троттлинг

| Scope | Эндпоинты | Лимит |
|---|---|---|
| `login` | `/login/` | 10 / минуту |
| `register` | `/register/` | 5 / час |
| `password_change` | `/password/change/` | 10 / минуту |
| `password_reset` | `/password/reset/` | 5 / час |
| `password_reset_confirm` | `/password/reset/confirm/` | 20 / час |

Счётчик ведётся по IP (для неавторизованных) или по пользователю (для
`password_change`). При превышении — `429` с `Retry-After` в теле.

## Сроки жизни токенов

| Токен | Время жизни |
|---|---|
| `access` | 1 час |
| `refresh` | 1 сутки |

`refresh` не ротируется; отзывается через `/logout/`, а также принудительно —
при смене и сбросе пароля.

## Схема входа

```
[Android: Google Sign-In / VK SDK / email+пароль]
        │  (ID token / access token / email+password)
        ▼
[Auth-сервис: /authApi/login/...]
        │  валидация + создание/поиск пользователя
        ▼
[БД: User + UserAuthProvider (+ PasswordResetCode)]
        │  выдача JWT
        ▼
[Auth-сервис: {refresh, access, user}]
        │
        ▼
[Android: хранит JWT, использует access для защищённых запросов]
```

## Схема сброса пароля

```
[Android] --email--> POST /password/reset/
[Сервис]  --код (6 цифр, 15 мин)--> письмо
[Android] --email + code + new_password--> POST /password/reset/confirm/
[Сервис]  --новый пароль, отзыв refresh--> 200
[Android] --email + new_password--> POST /login/
```

---

## Эксплуатация (Reg.ru, Passenger + Python 3.8)

| Что | Значение |
|---|---|
| Корень сайта | `/var/www/u2348486/data/www/auth.lemniscatasaa.ru/` |
| Проект | `.../authAndroid/` |
| Python | `/opt/python/python-3.8.8/bin/python` |
| Django admin | `/panel/` (путь `/admin/` перехватывает хостинг) |
| Стек | Django 4.2, DRF 3.15, SimpleJWT 5.3, MySQL, Python 3.8 |

Применение миграций и проверка:

```bash
cd /var/www/u2348486/data/www/auth.lemniscatasaa.ru/authAndroid
/opt/python/python-3.8.8/bin/python manage.py check
/opt/python/python-3.8.8/bin/python manage.py migrate --noinput
```

Перезапуск приложения: создать файл `.restart-app` в корне сайта (самоудаляется хостингом).

## Переменные окружения

| Переменная | Назначение |
|---|---|
| `GOOGLE_CLIENT_ID` | Web Client ID для проверки `aud` Google ID token |
| `SECRET_KEY` | секрет Django (сейчас в коде, для продакшена вынести) |
| `EMAIL_BACKEND` … | SMTP для писем сброса пароля |
| `DEFAULT_FROM_EMAIL` | адрес отправителя |
| `PASSWORD_RESET_CODE_TTL_MINUTES` | время жизни кода (по умолчанию 15) |
| `PASSWORD_RESET_MAX_ATTEMPTS` | попыток ввода кода (по умолчанию 5) |

## Известные ограничения

- `DEBUG = True` — перед продакшеном выключить (иначе видны трейсбеки и `debug_code`).
- `GOOGLE_CLIENT_ID` — плейсхолдер; без реального значения Google-вход вернёт `401`.
- SMTP не настроен: письма сброса пароля не уходят (backend = console).
- Коллизия email: если пользователь уже зарегистрирован по паролю и потом входит
  через Google/VK с тем же email, соц-вход может вернуть `500` (создание
  `username`-дубликата). Требуется отдельное решение (linking аккаунтов).
