# План доработок приложения "LifePet" (MyPet)

## 📌 Текущий функционал (что уже есть)

На основе анализа исходного кода (`/app/src/main/java/com/smock210/mypet/`) и ресурсов (`/res/`):

### 1. Аутентификация через VK (ВКонтакте)
- **VK SDK v3.5.2** интегрирован в `MainActivity`
- Поддерживается логин VK с запросом scope (WALL, EMAIL, PHONE)
- После входа — запрос данных пользователя через `VKUsersCommand`
- Отображение аватара (Picasso) и имени (`firstName + lastName`)

### 2. Отображение списка питомцев
- **RecyclerView** c кастомным адаптером `NewPetAdapt.NewPetadapt`
- Элемент списка: `list_row.xml` (ImageView + TextView)
- Поддержка кликов и долгих кликов (смена фона)

### 3. Навигация (Jetpack Navigation)
- **nav_graph** — `FirstFragment` ↔ `SecondFragment` (в `MainLoginForm`)
- **nav_graph2** — `First2Fragment` ↔ `Second2Fragment` (не привязана)

### 4. База данных (Room + SQLite)
- **Room** с сущностью `PetData`, DAO `EmployeeDao`, `AppDatabase` (непубличные — дефект)
- **SQLiteOpenHelper**-заглушка `SQLPetDatabase` (с TODO)

### 5. Firebase Auth (задекларирован, не используется)

### 6. Регистрация
- `RegistrActivity` — пустая активность-заглушка

### 7. Конфигурация темы
- Material Components (DayNight, DarkActionBar)
- 4 density-слота для `user_placeholder.png`
- Адаптивная тема: `values-night/themes.xml` (purple_200)

---

## 🎯 Предлагаемый новый функционал

### Фаза 1: Исправление базовых дефектов (MVP)

| № | Задача | Обоснование | Статус |
|---|---|---|---|
| 1.1 | **Зарегистрировать `VkApplication` в манифесте** | `android:name=".VkApplication"` — без этого обработка истечения токена не работает | ✅ выполнено |
| 1.2 | **Сделать Room-сущности публичными** | Переместить `PetData`, `EmployeeDao`, `AppDatabase` на уровень пакета или сделать их публичными top-level классами | ✅ выполнено |
| 1.3 | **Реализовать `SQLPetDatabase.onCreate/onUpgrade`** | Убрать TODO, реализовать создание таблицы `PetData` | ✅ выполнено (заменено Room, класс удалён) |
| 1.4 | **Исправить пакет `clasess` → `classes`** | Рефакторинг нейминга для читаемости | ✅ выполнено |

> 21.10.2026: дополнительно исправлен баг передачи `pet_id` (главный дефект), добавлены разрешения в манифест, удалён мёртвый код, DAO `insert` теперь возвращает id. Подробности — в `FIX_PLAN.md`.

### Фаза 2: Расширение функционала питомца

| № | Задача | Описание |
|---|---|---|
| 2.1 | **Экран создания/редактирования питомца** | ✅ Форма: имя, вид, фото (галерея/камера), заметки. Сохранять в Room |
| 2.2 | **Экран деталей питомца** | ✅ Карточка: фото (крупно), описание, кнопка «Редактировать», галерея, график веса |
| 2.3 | **Возможность загружать фото из галереи** | ✅ `ActivityResultContracts.GetContent()` + галерея фото питомца |
| 2.4 | **Список питомцев — заглушка "нет питомцев"** | ✅ Заглушка «Добавьте первого питомца» |
| 2.5 | **Свайп-удаление питомца** | ✅ `ItemTouchHelper.SimpleCallback()` + Undo-Snackbar |

### Фаза 3: Взаимодействие с VK (углубление)

| № | Задача | Описание |
|---|---|---|
| 3.1 | **Загрузка списка друзей VK** | ✅ `VKUsersCommand(uids)` — команда поддерживает выбор пользователей |
| 3.2 | **Публикация в VK** | ✅ `VKWallPostCommand` — кнопка «Опубликовать в VK» в карточке питомца |
| 3.3 | **VK Groups API** | ⏳ Не сделано (P3) |

### Фаза 4: UI/UX улучшения

| № | Задача | Описание |
|---|---|---|
| 4.1 | **Светлая/тёмная тема (ручной переключатель)** | ✅ Пункт меню «Сменить тему», режим в DataStore |
| 4.2 | **Анимации переходов** | ⏳ Не сделано (P3) |
| 4.3 | **Splash Screen** | ✅ `SplashScreen` API (`core-splashscreen:1.0.1`) |
| 4.4 | **Material 3 (Dynamic Colors)** | ✅ Темы `Theme.Material3.*` + `DynamicColors` для Android 12+ |

### Фаза 5: Безопасность и данные

| № | Задача | Описание |
|---|---|---|
| 5.1 | **EncryptedSharedPreferences** | ✅ `SecurePreferencesManager` (кэш профиля VK) |
| 5.2 | **Biometric (отпечаток)** | ✅ `BiometricAuthHelper` — блокировка приложения при старте |
| 5.3 | **DataStore (вместо SharedPreferences)** | ✅ `DataStoreProvider` — тема и флаг биометрии |

### Фаза 6: Тестирование и CI

| № | Задача | Описание |
|---|---|---|
| 6.1 | **Unit-тесты для DAO** | ✅ 11 тестов `DatabaseTest` (DAO + изоляция по petId) + 3 `MigrationTest` + 6 `FeedingCalculatorTest` |
| 6.2 | **UI-тесты (Espresso)** | ⏳ Не сделано (P3) |
| 6.3 | **CI (GitHub Actions)** | ✅ `.github/workflows/android.yml` — сборка + unit-тесты |

---

## 🧱 Архитектурные решения

### Рекомендуемый стек

```
UI Layer      → Activities + Fragments + ViewBinding
Domain Layer  → ViewModel + LiveData/StateFlow
Data Layer    → Room (PetData) + VK SDK (VKUser) + Firebase
DI            → Hilt/Dagger (опционально)
```

### Текущие проблемы

```
Все P0–P2 пункты закрыты (см. FIX_PLAN.md, журнал выполнения).
Остались задачи уровня P3:
1. UI-тесты (Espresso) не написаны — покрытие только unit-тестами
2. Реальный поиск клиник по Google Places API — нужен ключ Google
3. VK Groups API не реализован
4. Анимации переходов между экранами не добавлены
```

---

## 📅 Приоритеты

```
P0 (Критично): 1.1, 1.2, 1.3   — Без этого приложение не работает
P1 (Важно):   2.1, 2.2, 5.1   — Основной UX
P2 (Желательно): 3.1, 4.1, 4.2 — Дополнительные фичи
P3 (Косметика): 4.3, 4.4, 5.2 — Полировка
```

---

## 📝 Примечания

- **Firebase Auth** — отключён (21.10.2026): выбран вход через VK, зависимости закомментированы, `FirebaseAuthHelper` удалён. `google-services.json` можно удалить из `app/`.
- **VK SDK** — обновлён с 3.5.2 до 4.1.0, реализованы вход (`VK.login`), профиль (`VKUsersCommand`) и публикация фото (`VKWallPostCommand`)
- **Android 12+ (API 31)** — целевая SDK. Можно поднять до 33 для поддержки новых API (SplashScreen, PhotoPicker)

---

*Создано: 25 июня 2026 г.*