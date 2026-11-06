# Инструкция для LLM: Android-клиент для Auth API

> Документ-задание. Читай целиком перед началом. Все контракты API ниже **проверены живыми запросами** к рабочему серверу — менять их нельзя.

---

## 0. Правила работы (обязательно)

1. **Не меняй контракт API.** Пути, тела запросов/ответов, коды статусов — фиксированы (раздел 2).
2. **Не меняй сервер.** Если для задачи объективно нужна правка бэкенда — не правь его молча, а вынеси в отдельный раздел отчёта «Требуется на сервере».
3. **Никаких секретов в APK.** В приложении нет ни `client_secret`, ни пароля БД, ни `SECRET_KEY`. Только публичные идентификаторы (Web Client ID, VK App ID).
4. **Только HTTPS.** `http://` не использовать, `usesCleartextTraffic` не включать.
5. **Все сетевые вызовы — вне main thread** (корутины/`Dispatchers.IO`).
6. **Токены — только в `EncryptedSharedPreferences`**, не в `SharedPreferences`, не в `BuildConfig`, не в логах. Логирование тел запросов с токенами запрещено.
7. Версии зависимостей в разделе 4 — ориентир; если версия не резолвится, возьми ближайшую актуальную и зафиксируй это в отчёте.
8. Работай по шагам раздела 8 и после каждого шага проект должен собираться (`./gradlew assembleDebug`).

---

## 1. Контекст

Есть готовый backend на Django REST Framework, который:

- принимает токены сторонних провайдеров (Google ID token, VK access token);
- находит/создаёт пользователя у себя;
- выдаёт **собственные JWT** (`access` + `refresh`);
- отдаёт профиль по `access`-токену.

Задача Android-приложения: дать пользователю войти через Google или VK, получить и хранить наши JWT, показывать профиль и уметь выйти.

```
[Android] --(Google ID token / VK access token)--> [Auth API]
[Auth API] --(создаёт/находит юзера, выдаёт JWT)--> [Android]
[Android] --(Authorization: Bearer <access>)--> [Auth API /authApi/me/]
```

### Базовый URL

```
https://www.auth.lemniscatasaa.ru
```

Проверка живости сервиса (можно выполнить из терминала):

```bash
curl -s https://www.auth.lemniscatasaa.ru/
# {"status": "ok", "service": "authApi", "api": "/authApi/", "endpoints": [...]}
```

---

## 2. Контракт API (проверено, менять нельзя)

Все тела — `Content-Type: application/json`, кодировка UTF-8.
Базовый префикс: `/authApi/`.

### 2.1 `POST /authApi/register/`

Регистрация по email + паролю (нужна только если делаешь форму email/пароль; для соцвхода не требуется).

Запрос:
```json
{ "email": "user@example.com", "password": "secret-password" }
```

Ответы:
| Код | Тело |
|---|---|
| `201` | `{"message": "User registered successfully", "refresh": "<jwt>", "access": "<jwt>", "user": {...}}` |
| `400` | `{"error": "Email and password are required"}` |
| `400` | `{"error": "User already exists"}` |
| `400` | `{"error": "Enter a valid email address.", "details": {...}}` |
| `400` | `{"error": "This password is too short. It must contain at least 8 characters.", "details": {...}}` |
| `429` | троттлинг: 5 запросов в час на IP |

> **Важно:** регистрация сразу возвращает `refresh` + `access` — отдельный вызов
> `/authApi/login/` не нужен. Email нормализуется (trim + lower), пароль — минимум
> 8 символов и не из списка типовых (`validate_password`).

### 2.2 `POST /authApi/login/`

Запрос:
```json
{ "email": "user@example.com", "password": "secret-password" }
```

Ответы:
| Код | Тело |
|---|---|
| `200` | `{"refresh": "<jwt>", "access": "<jwt>"}` |
| `400` | `{"error": "Email and password are required"}` |
| `401` | `{"error": "Invalid credentials"}` |
| `429` | троттлинг: 10 запросов в минуту на IP |

Поиск идёт по email (регистр не важен), поэтому вход по паролю работает и для
аккаунтов, созданных через Google/VK (у них `username` может отличаться от email).

### 2.3 `POST /authApi/login/google/`

Запрос (основной путь — из Google Sign-In):
```json
{ "id_token": "<Google ID token>" }
```

Ответ `200`:
```json
{
  "refresh": "<jwt>",
  "access": "<jwt>",
  "user": { "id": 1, "username": "user@example.com", "email": "user@example.com" }
}
```

Ошибки:
| Код | Тело | Причина |
|---|---|---|
| `401` | `{"error": "Invalid Google token"}` | подпись/issuer/audience/срок не сошлись |
| `401` | `{"error": "Failed to resolve Google user"}` | токен валиден, но пользователь не разрешился |
| `400` | `{"error": "Either id_token or provider_user_id are required"}` | пустое тело |

**Критично для Android:** сервер проверяет `aud` токена = `GOOGLE_CLIENT_ID` на сервере. Это должен быть **Web Client ID** (тип «Web application») из Google Cloud Console, а не Android Client ID. Подробнее — раздел 5.1.

### 2.4 `POST /authApi/login/vk/`

Запрос:
```json
{ "access_token": "<VK access token>" }
```

Ответ `200` — как у Google (`refresh`, `access`, `user`).

Ошибки:
| Код | Тело |
|---|---|
| `400` | `{"error": "access_token is required"}` |
| `401` | `{"error": "Invalid VK token"}` |

Сервер сам валидирует токен через `https://api.vk.com/method/users.get` (v=5.131, fields=`email,first_name,last_name`). Клиенту **не нужно** самому ходить в VK API за профилем.

### 2.5 `POST /authApi/token/refresh/`

Запрос:
```json
{ "refresh": "<refresh-jwt>" }
```

Ответы:
| Код | Тело |
|---|---|
| `200` | `{"access": "<new access-jwt>"}` |
| `400` | `{"refresh": ["This field is required."]}` |
| `401` | `{"detail": "Token is blacklisted", "code": "token_not_valid"}` |

> Refresh-токен **не ротируется**: в ответе нового `refresh` нет. Старый остаётся валидным до истечения срока или до logout.

### 2.6 `POST /authApi/logout/`

Запрос:
```json
{ "refresh": "<refresh-jwt>" }
```

Ответы:
| Код | Тело |
|---|---|
| `200` | `{"detail": "Successfully logged out"}` |
| `400` | `{"error": "Refresh token is required"}` |
| `401` | `{"error": "Invalid refresh token"}` |

> После logout сервер отзывает **только refresh**. `access` технически живёт до `exp`, поэтому клиент обязан сам удалить оба токена и очистить состояние сессии.

### 2.7 `GET /authApi/me/`

Заголовок: `Authorization: Bearer <access>`

Ответы:
| Код | Тело |
|---|---|
| `200` | `{"id": 1, "username": "...", "email": "...", "first_name": "...", "last_name": "..."}` |
| `401` | `{"detail": "Authentication credentials were not provided.", "code": "not_authenticated"}` |
| `401` | `{"detail": "Given token not valid for any token type", "code": "token_not_valid"}` |

### 2.8 Сроки жизни токенов

| Токен | Время жизни |
|---|---|
| `access` | 1 час |
| `refresh` | 1 сутки |

`refresh` не ротируется, но принудительно отзывается при смене и сбросе пароля.

### 2.9 `POST /authApi/password/change/`

Смена пароля. Требует `Authorization: Bearer <access>`.

Запрос:
```json
{ "old_password": "secret-password", "new_password": "new-secret-password" }
```

Ответы:
| Код | Тело |
|---|---|
| `200` | `{"message": "Password changed successfully. Please sign in again."}` |
| `400` | `{"error": "Old password is incorrect"}` |
| `400` | `{"error": "New password must be different from the old one"}` |
| `400` | `{"error": "Password does not meet requirements", "details": [...]}` |
| `400` | `{"error": "This account has no password. Use password reset to set one."}` |
| `401` | нет/просрочен `access` |
| `429` | троттлинг: 10 в минуту |

> После успешной смены **все refresh-токены пользователя отзываются**. Клиент
> обязан очистить `TokenStore` и увести пользователя на экран Login — иначе
> следующий refresh вернёт `401`.

### 2.10 `POST /authApi/password/reset/`

Запрос кода сброса. Ответ **всегда `200`** (защита от перебора email).

Запрос:
```json
{ "email": "user@example.com" }
```

Ответ:
```json
{"message": "If this email exists, a reset code has been sent"}
```

При `DEBUG = True` на сервере дополнительно приходит `"debug_code": "482913"` —
только для отладки, в UI его показывать не нужно.

| Код | Тело |
|---|---|
| `400` | `{"error": "Enter a valid email address."}` |
| `429` | троттлинг: 5 в час на IP |

Код: 6 цифр, живёт 15 минут, максимум 5 попыток ввода, хранится в виде хеша.
Повторный запрос инвалидирует предыдущие коды.

### 2.11 `POST /authApi/password/reset/confirm/`

Запрос:
```json
{ "email": "user@example.com", "code": "482913", "new_password": "new-secret-password" }
```

Ответы:
| Код | Тело |
|---|---|
| `200` | `{"message": "Password has been reset successfully"}` |
| `400` | `{"error": "Invalid or expired code"}` |
| `400` | `{"error": "Password does not meet requirements", "details": [...]}` |
| `429` | троттлинг: 20 в час на IP |

> Слабый пароль проверяется **до** кода — код не «сгорает» при ошибке валидации.
> После успеха все refresh-токены отозваны: нужно заново вызвать `/authApi/login/`.

### 2.12 Троттлинг (клиент обязан обрабатывать `429`)

| Scope | Лимит |
|---|---|
| `/login/` | 10 / минуту |
| `/register/` | 5 / час |
| `/password/change/` | 10 / минуту |
| `/password/reset/` | 5 / час |
| `/password/reset/confirm/` | 20 / час |

При `429` показывать текст из `detail` («Слишком много попыток, попробуйте позже»)
и **не** разлогинивать. Если в теле есть «Expected available in N seconds» —
можно показать таймер.

### 2.13 Коды, которые клиент обязан обрабатывать

| Код | Действие клиента |
|---|---|
| `400` | показать `error`/первое поле из тела |
| `401` при наличии сессии | попытаться refresh (1 раз), при неудаче — разлогинить |
| `401` без сессии | показать экран логина |
| `404` (fallback-путь Google) | показать «пользователь не найден» |
| `405` | ошибка реализации (метод не тот) — в отчёт |
| `429` | «Слишком много попыток», без разлогина |
| `5xx` | «Сервис недоступен», предложить повторить; не разлогинивать |

---

## 3. Требования к приложению

### 3.1 Технологии

| Параметр | Значение |
|---|---|
| Язык | Kotlin |
| UI | Jetpack Compose + Material 3 |
| minSdk | 24 |
| compileSdk / targetSdk | 34 |
| JVM target | 17 |
| Сеть | Retrofit 2 + OkHttp 4 |
| JSON | kotlinx.serialization |
| Асинхронность | Coroutines + Flow |
| Хранение токенов | `androidx.security:security-crypto` (EncryptedSharedPreferences) |
| DI | без Hilt: простые object-синглтоны / ручной DI (проще и быстрее) |

### 3.2 Экраны (минимум)

1. **Login** — две кнопки: «Войти с Google», «Войти с VK»; форма email/пароль;
   ссылки «Зарегистрироваться» и «Забыли пароль?». Индикатор загрузки, текст ошибки.
2. **Register** — email + пароль + повтор пароля. После успеха сразу сохранить
   полученные токены и открыть Profile (отдельный login не нужен).
3. **Password change** (из Profile) — старый пароль, новый, повтор.
   После успеха очистить токены и вернуться на Login.
4. **Password reset** — два шага:
   - ввод email → `POST /password/reset/`;
   - ввод кода из письма + новый пароль → `POST /password/reset/confirm/` → Login.
5. **Profile** — `id`, `username`, `email`, `first_name`, `last_name`; кнопки
   «Сменить пароль» и «Выйти».
6. **Splash / gate** — при старте: есть валидная сессия → Profile, иначе → Login.

### 3.3 Поведение

- При старте приложения, если токены есть, сразу открывать Profile и обновлять данные из `/authApi/me/`.
- Если `/authApi/me/` вернул `401` — один раз попробовать refresh, при неудаче разлогинить.
- Кнопки входа/регистрации блокируются на время запроса (защита от двойного тапа).
- Ошибки `400` показывать текстом из `error` (при наличии `details` — по полям формы).
- `429` — «Слишком много попыток, попробуйте позже», без разлогина.
- Никаких «тихих» падений: любая ошибка показывается пользователю человеческим текстом.

---

## 4. Зависимости (Gradle)

`gradle/libs.versions.toml` или прямо в `app/build.gradle.kts`:

```kotlin
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // только для debug, см. 5.5
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Secure storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google: Credential Manager + Google ID
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")

    // VK ID SDK
    implementation("com.vk.id:vkid:2.5.0")
}
```

Плагины: `kotlin("android")`, `kotlin("plugin.serialization")`, `com.android.application`.

> Если `com.vk.id:vkid` не резолвится — проверь актуальную версию в документации VK ID и зафиксируй её в отчёте. Не откатывайся на устаревший `com.vk.android:sdk` без явной причины.

---

## 5. Ключевые технические моменты (частые грабли)

### 5.1 Google: два разных Client ID

В Google Cloud Console нужны **два** OAuth client:

| Тип | Назначение | Где используется |
|---|---|---|
| **Android** | привязка к package name + SHA-1 | внутри Google Play Services |
| **Web application** | `aud` для серверной проверки | `serverClientId` в Credential Manager **и** `GOOGLE_CLIENT_ID` на сервере |

- В приложении **нельзя** подставлять Android Client ID в `setServerClientId(...)` — иначе сервер вернёт `401 Invalid Google token` (несовпадение `aud`).
- SHA-1 debug-ключа: `./gradlew signingReport`.
- Значение `serverClientId` и серверный `GOOGLE_CLIENT_ID` должны быть **буквально одинаковыми**.

### 5.2 Google: Credential Manager (актуальный API)

```kotlin
suspend fun signInWithGoogle(activity: Activity): String? {
    val credentialManager = CredentialManager.create(activity)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else null
    } catch (e: GetCredentialException) {
        null // отмена пользователем / нет аккаунтов
    }
}
```

Полученный `idToken` уходит на сервер как `{"id_token": idToken}`. **Дополнительно проверять токен на клиенте не нужно.**

### 5.3 VK: получение access token

Через VK ID SDK:

```kotlin
VKID.instance.authorize(
    activity = activity,
    params = VKID.AuthParams(scope = "email")
) { result ->
    when (result) {
        is VKIDAuthResult.Success -> {
            val accessToken: String = result.token.accessToken
            // отправить на сервер как {"access_token": accessToken}
        }
        is VKIDAuthResult.Fail -> { /* показать ошибку */ }
    }
}
```

- В `AndroidManifest.xml` VK ID требует объявления `VKIDActivity` и/или собственного `CallbackActivity` — сверься с документацией установленной версии SDK. Ошибка конфигурации обычно выглядит как «ничего не происходит» при нажатии кнопки.
- **Email от VK приходит не всегда** (только если у приложения есть доступ и пользователь дал согласие). Приложение обязано корректно работать с пустым `email`: сервер в этом случае создаст пользователя с `username = vk_<id>`.
- Не вызывай `users.get` самостоятельно — сервер уже это делает.

### 5.4 Хранение токенов

```kotlin
class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    var access: String?
        get() = prefs.getString("access", null)
        set(v) { prefs.edit().putString("access", v).apply() }
    var refresh: String?
        get() = prefs.getString("refresh", null)
        set(v) { prefs.edit().putString("refresh", v).apply() }
    fun clear() { prefs.edit().clear().apply() }
}
```

### 5.5 OkHttp: Bearer + автоматический refresh

Требования к реализации:

1. `AuthInterceptor` добавляет `Authorization: Bearer <access>`, если токен есть и запрос **не** идёт на `/authApi/token/refresh/`, `/authApi/login/`, `/authApi/register/`.
2. `TokenAuthenticator` (OkHttp `Authenticator`) реагирует на `401`:
   - под `Mutex`/`synchronized` вызывает `POST /authApi/token/refresh/` с текущим `refresh`;
   - при `200` сохраняет новый `access` и повторяет исходный запрос;
   - при `401` очищает токены и сигналит «сессия истекла» (Flow/StateFlow → UI уводит на Login);
   - **не более одной попытки** на запрос (не зацикливаться).
3. `Authenticator` синхронный: используй `runBlocking` **внутри** него (это отдельный поток OkHttp, main thread не блокируется).
4. Логирование (`HttpLoggingInterceptor`) — **только** `BuildConfig.DEBUG` и уровень `BASIC` (не `BODY`: в теле токены).

### 5.6 Ошибки сети

- Таймауты: connect 15s, read 20s.
- `IOException` → «Нет соединения».
- `5xx` → «Сервис недоступен, попробуйте позже».
- Никогда не разлогинивать пользователя при `5xx`/`IOException`.

---

## 6. Структура проекта

```
app/src/main/java/<package>/
├── App.kt                        // Application, инициализация VK ID / DI
├── BuildConfig                   // BASE_URL, GOOGLE_WEB_CLIENT_ID, VK_APP_ID
├── data/
│   ├── api/
│   │   ├── AuthApi.kt            // Retrofit-интерфейс
│   │   ├── ApiClient.kt          // OkHttp + Retrofit + Interceptor + Authenticator
│   │   └── dto/                  // LoginRequest, GoogleLoginRequest, VkLoginRequest,
│   │                             // RefreshRequest, LogoutRequest, TokenPair, RefreshResponse, UserDto
│   ├── TokenStore.kt
│   └── AuthRepository.kt         // единственная точка работы с API для UI
├── auth/
│   ├── GoogleAuthClient.kt
│   └── VkAuthClient.kt
└── ui/
    ├── login/LoginScreen.kt, LoginViewModel.kt
    ├── profile/ProfileScreen.kt, ProfileViewModel.kt
    └── theme/
```

### 6.1 Retrofit-интерфейс (эталон)

```kotlin
interface AuthApi {
    @POST("authApi/register/")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("authApi/login/")
    suspend fun login(@Body body: LoginRequest): TokenPair

    @POST("authApi/login/google/")
    suspend fun loginGoogle(@Body body: GoogleLoginRequest): SocialLoginResponse

    @POST("authApi/login/vk/")
    suspend fun loginVk(@Body body: VkLoginRequest): SocialLoginResponse

    @POST("authApi/token/refresh/")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @POST("authApi/logout/")
    suspend fun logout(@Body body: LogoutRequest): MessageResponse

    @GET("authApi/me/")
    suspend fun me(): UserDto

    @POST("authApi/password/change/")
    suspend fun changePassword(@Body body: PasswordChangeRequest): MessageResponse

    @POST("authApi/password/reset/")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequest): MessageResponse

    @POST("authApi/password/reset/confirm/")
    suspend fun confirmPasswordReset(@Body body: PasswordResetConfirmRequest): MessageResponse
}
```

`RegisterResponse` содержит `message`, `refresh`, `access`, `user` (см. 2.1) —
токены сохранять сразу, повторный `login()` не вызывать.

`MessageResponse` — `{ "message": "...", "error": "..."?, "details": ...? }`
(все поля nullable, кроме `message`).

DTO — строго по разделу 2. `@SerialName` использовать только если имя поля в JSON отличается от Kotlin-имени.

`BASE_URL` для Retrofit **обязан** заканчиваться на `/`:
`"https://www.auth.lemniscatasaa.ru/"`.

---

## 7. Конфигурация сборки

`app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "BASE_URL", "\"https://www.auth.lemniscatasaa.ru/\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"<WEB_CLIENT_ID>.apps.googleusercontent.com\"")
        buildConfigField("String", "VK_APP_ID", "\"<VK_APP_ID>\"")
    }
    buildFeatures { buildConfig = true; compose = true }
}
```

Если реальных идентификаторов ещё нет — оставь плейсхолдеры и **явно перечисли их в отчёте** как «требуется от заказчика». Приложение должно собираться с плейсхолдерами и показывать понятную ошибку при попытке входа, а не падать.

`AndroidManifest.xml`:
- `<uses-permission android:name="android.permission.INTERNET" />`
- никакого `usesCleartextTraffic="true"`
- активность для VK callback — по документации VK ID SDK.

---

## 8. Порядок реализации (чек-лист для LLM)

1. **Каркас**: Gradle-проект, Compose, тема, пустые экраны, `BuildConfig`.
2. **Сеть**: DTO + `AuthApi` + `ApiClient` (без авторизации) + `AuthRepository`.
3. **Хранилище**: `TokenStore` на EncryptedSharedPreferences.
4. **Email/пароль**: экран Login (форма), `login` → сохранение токенов; экран Register
   (`register` сразу возвращает токены — не вызывать `login` повторно).
5. **Профиль**: `GET /authApi/me/` с Bearer, вывод полей, кнопка выхода (`logout` + очистка + навигация).
6. **Смена пароля**: экран из Profile (`/password/change/`), после `200` — очистить токены и на Login.
7. **Сброс пароля**: два экрана (`/password/reset/` → `/password/reset/confirm/`),
   обработка `debug_code` только для отладки, после `200` — на Login.
8. **Google**: Credential Manager → `id_token` → `/authApi/login/google/`.
9. **VK**: VK ID SDK → `access_token` → `/authApi/login/vk/`.
10. **Автообновление**: `TokenAuthenticator` + обработка «сессия истекла».
11. **Полировка**: состояния загрузки/ошибок, блокировка кнопок, обработка `429` и отсутствия сети.
12. **Самопроверка** по разделу 9 и отчёт по разделу 10.

После каждого шага: `./gradlew assembleDebug` должен проходить.

---

## 9. Приёмка (как проверять)

### 9.1 Бэкенд без приложения (curl)

```bash
B=https://www.auth.lemniscatasaa.ru
E="test_$(date +%s)@example.com"

# 1) регистрация: 201 + refresh/access
curl -s -X POST -H 'Content-Type: application/json' \
  -d "{\"email\":\"$E\",\"password\":\"pass12345\"}" $B/authApi/register/

# 2) логин: 200 + refresh/access
curl -s -X POST -H 'Content-Type: application/json' \
  -d "{\"email\":\"$E\",\"password\":\"pass12345\"}" $B/authApi/login/

# 3) сброс пароля: 200 (+ debug_code при DEBUG=True)
curl -s -X POST -H 'Content-Type: application/json' \
  -d "{\"email\":\"$E\"}" $B/authApi/password/reset/
```

### 9.2 Проверки в приложении

| # | Сценарий | Ожидаемо |
|---|---|---|
| 1 | Первый запуск без токенов | экран Login |
| 2 | Email/пароль: верные данные | Profile, поля заполнены |
| 3 | Email/пароль: неверный пароль | сообщение, остаёмся на Login |
| 4 | Регистрация: валидные данные | Profile сразу, без повторного логина |
| 5 | Регистрация: пароль `"1"` | ошибка «минимум 8 символов», форма не отправлена |
| 6 | Регистрация: занятый email | «User already exists» |
| 7 | Google: вход | Profile; в логах debug нет токенов |
| 8 | VK: вход | Profile; при пустом email приложение не падает |
| 9 | Перезапуск приложения | сразу Profile (сессия сохранена) |
| 10 | Logout | Login; после перезапуска — Login |
| 11 | Подмена access на мусор → запрос `/me/` | однократный refresh; если refresh невалиден → Login |
| 12 | Смена пароля: верный старый | `200`, токены очищены, экран Login |
| 13 | Смена пароля: неверный старый | «Old password is incorrect», сессия сохранена |
| 14 | Сброс пароля: известный email | письмо/код, переход ко второму шагу |
| 15 | Сброс пароля: неизвестный email | тот же `200` (без раскрытия), переход ко второму шагу |
| 16 | Сброс пароля: неверный код | «Invalid or expired code», можно попробовать снова |
| 17 | Сброс пароля: успешный | вход с новым паролем работает |
| 18 | 11 быстрых неверных логинов | на 11-м `429`, приложение не разлогинивает |
| 19 | Авиарежим при запросе | «Нет соединения», **без** разлогина |
| 20 | Двойной тап по кнопке входа | один запрос, кнопка заблокирована |
| 21 | Отмена выбора Google-аккаунта | без краша, без ошибки-«простыни» |

### 9.3 Запрещено в готовом коде

- `Log.d`/`println` с токенами или телами ответов;
- `SharedPreferences` для токенов;
- `GlobalScope`;
- хранение `client_secret`/паролей;
- `allowBackup="true"` вместе с токенами в незашифрованном хранилище.

---

## 10. Что приложить в отчёте

1. Список созданных файлов и их назначение.
2. Итоговые версии зависимостей (что реально зарезолвилось).
3. Результат `./gradlew assembleDebug`.
4. Таблица пройденных пунктов раздела 9.2 (что проверено, что нет и почему).
5. **Требуется от заказчика**: реальный Web Client ID, VK App ID, SHA-1 отпечаток, package name.
6. **Требуется на сервере** (если выявилось): например, обновить `GOOGLE_CLIENT_ID` в `settings.py` — с точной формулировкой, без самостоятельных правок.

---

## 11. Справочно: состояние сервера

| Что | Значение |
|---|---|
| API | `https://www.auth.lemniscatasaa.ru/authApi/` |
| Health | `GET https://www.auth.lemniscatasaa.ru/` → `{"status":"ok"}` |
| Админка Django | `/panel/` (путь `/admin/` перехватывается хостингом — не использовать) |
| Стек сервера | Django 4.2, DRF 3.15, SimpleJWT 5.3, MySQL, Python 3.8 |
| `GOOGLE_CLIENT_ID` на сервере | **пока плейсхолдер** — без реального значения Google-вход вернёт `401` |
| JWT | `access` 1 ч, `refresh` 1 сутки, refresh-токен не ротируется, logout через blacklist |
| Email/пароль | регистрация (+токены), вход, валидация, throttling — работают |
| Смена пароля | работает, при успехе отзываются все refresh-токены |
| Сброс пароля | работает через код в БД (6 цифр, 15 мин, 5 попыток) |
| Почта | `EMAIL_BACKEND = console` — письма **не уходят**, код виден в `debug_code` при `DEBUG=True` |
| `DEBUG` | `True` — на продакшене выключить (убирает `debug_code` и трейсбеки) |

> Итог: email/пароль, VK и сброс пароля можно тестировать сразу. Для Google нужно,
> чтобы Web Client ID был прописан **и** в приложении, **и** в `settings.py` сервера.
> Для реальной отправки писем нужен SMTP (переменные `EMAIL_*`).
