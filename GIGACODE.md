# MyPet — Android-приложение "LifePet"

## 📋 Обзор проекта

**MyPet** (отображаемое имя: **LifePet**) — это Android-приложение на Kotlin, разработанное как "дневник питомца" с интеграцией VK (ВКонтакте). Приложение использует VK SDK 4.1.0 для аутентификации, профиля пользователя и публикации фото питомца на стену. Локальное хранение — Room (6 сущностей, миграции 1→6), навигация — через `Intent` (Jetpack Navigation удалён как мёртвый код), тема — Material 3 с Dynamic Color, есть локализация ru/en.

- **Тип**: Мобильное Android-приложение
- **Язык**: Kotlin (с небольшой примесью Java)
- **Пакет**: `com.smock210.mypet`
- **Текущее имя**: `LifePet` (из `strings.xml`)

---

## 🏗 Архитектура

Проект имеет стандартную для Android-студии структуру Gradle с одним модулем `app`.

### Ключевые компоненты

| Компонент | Назначение |
|---|---|
| `SplashActivity` | Лаунчер на SplashScreen API (`core-splashscreen`, тема `Theme.SplashScreen`), сразу открывает `MainActivity`. |
| `MainActivity` | Список питомцев (RecyclerView + `NewPetAdapt`), свайп-удаление с Undo-Snackbar, поиск через `SearchView`, options-menu `title_menu.xml`. |
| `MainDetailActivity` | Карточка питомца: фото-галерея, запись видео, публикация в VK, график веса, переход в `health.HealthActivity`. |
| `AddPetActivity` | Хост `ui.AddPetFragment` — форма добавления **и редактирования** питомца (`EXTRA_PET_ID`). |
| `health.HealthActivity` | TabLayout + ViewPager: `VaccinationFragment`, `VetVisitFragment`. |
| `VkApplication` | Custom `Application`, обрабатывает истечение VK-токена, применяет Material 3 Dynamic Color. Зарегистрирован в манифесте. |
| `ToolsActivity` | Универсальный контейнер экранов: калькулятор кормления, экспорт данных, оценка состояния, карта клиник, диагностика VK. |
| `NewPetAdapt` | Адаптер RecyclerView списка питомцев (`ListAdapter` + `DiffUtil`). |
| `sqlClass/*` | Room-слой: `AppDatabase` (v6, миграции 1→6), сущности `PetData`, `Vaccination`, `VetVisit`, `PetPhoto`, `WeightRecord`; DAO `EmployeeDao` (insert возвращает id), `HealthDao`, `PetPhotoDao`, `WeightDao`. Есть Flow-варианты запросов. |
| `viewmodel/PetViewModel` | AndroidViewModel со StateFlow (`pets`, `vaccinations`, `visits`, `photos`, `weights`), реактивно из Room; снапшот для Undo-удаления. |

### Навигация

- Jetpack Navigation **удалён** (21.10.2026): графы `nav_graph`/`nav_graph2` и шаблонные фрагменты (`MainLoginForm`, `First*/Second*`) удалены как мёртвый код. Переходы между экранами — через `Intent`.

### Зависимости

| Зависимость | Версия |
|---|---|
| Android Gradle Plugin | `8.8.0` |
| Kotlin Gradle Plugin | `1.9.24` |
| Gradle | `8.10.2` |
| compileSdk / targetSdk | `35` |
| minSdk | `23` |
| Java | `11` |
| Material Components | `1.12.0` (темы на Material 3 + Dynamic Color) |
| SplashScreen | `1.0.1` |
| ViewPager2 | `1.1.0` (вкладки «Здоровье») |
| Room | `2.6.1` |
| Picasso | `2.8` |
| VK SDK | `4.1.0` (вход через VK, scope PHOTOS/EMAIL/PHONE) |
| Firebase Auth | отключён (выбран VK; класс FirebaseAuthHelper удалён) |
| DataStore / security-crypto / Biometric | `1.1.6` / `1.1.0-alpha06` / `1.2.0-alpha05` |
| Корутины (+ play-services) | `1.9.0` / `1.9.0` |
| Maps / Location | `18.2.0` / `21.3.0` |
| Локализация | `values` (ru) + `values-en` (en) |

---

## 🚀 Сборка и запуск

### Сборка (debug)

```bash
./gradlew assembleDebug
```

### Установка на устройство

```bash
./gradlew installDebug
```

### Тесты

```bash
./gradlew test                # unit tests (JUnit4)
./gradlew connectedAndroidTest # instrumented tests (Espresso)
```

> ⚠️ **Примечание**: Перед сборкой убедитесь, что `google-services.json` (Firebase) и `local.properties` (Android SDK) настроены корректно.

---

## 📁 Структура ключевых файлов

```
MyPet-master/
├── build.gradle              # Корневой buildscript (AGP, Kotlin, javax.inject)
├── settings.gradle           # include ':app'
├── gradle.properties          # AndroidX, Jetifier, Kotlin code style
├── gradle/wrapper/
│   └── gradle-wrapper.properties  # Gradle 7.4
├── app/
│   ├── build.gradle          # Модуль app (аггрегация зависимостей)
│   ├── google-services.json  # Firebase конфигурация
│   ├── proguard-rules.pro    # Пустой (minifyEnabled=false)
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # 4 активности, только INTERNET permission
│   │   ├── java/com/smock210/mypet/  # Исходники
│   │   └── res/                   # Ресурсы
│   ├── src/test/             # Unit-тесты (ExampleUnitTest)
│   └── src/androidTest/      # Instrumented-тесты (ExampleInstrumentedTest)
└── GIGACODE.md               # Текущий файл
```

---

## 🧩 Обнаруженные проблемы (дефекты)

Статус на 21.10.2026 (частично исправлено, см. FIX_PLAN.md):

1. ~~`VkApplication` не зарегистрирован в манифесте~~ — **исправлено**.
2. ~~Room-сущности непубличные~~ — **исправлено** (`sqlClass/*` — top-level).
3. ~~`SQLPetDatabase` — заглушка~~ — **удалён** (заменён Room).
4. Пустой `proguard-rules.pro` — при `minifyEnabled=false` не критично, но при замене на `true` — сломается.
5. ~~Пакет `clasess`~~ — **исправлено** (`classes`).
6. ~~`FirstFragment` отсутствует~~ — граф `nav_graph` и `First/SecondFragment` **удалены** как неиспользуемые.
7. **Баг pet_id**: `MainActivity` не передавал `EXTRA_PET_ID` → записи писались с `petId = 0`. **Исправлено 21.10.2026**.
8. Не хватало разрешений `POST_NOTIFICATIONS`/`SCHEDULE_EXACT_ALARM`/`WRITE_EXTERNAL_STORAGE` (maxSdk 28)/`CAMERA` — **добавлены в манифест** (runtime-запросы ещё не везде сделаны).
9. Напоминания не планируются (никто не вызывает `NotificationHelper`) — см. FIX_PLAN.md, фаза 3.
10. Экраны без точек входа: `ExportDataFragment`, `FeedingCalculatorFragment`, `HealthScaleFragment`, `MapsFragment` — см. FIX_PLAN.md, фаза 2.
11. ~~`MainLoginForm`/`RegistrActivity` — мёртвые точки входа~~ — оба удалены; `menu_toggle_theme` теперь реализован (тема из DataStore).
12. **Firebase**: зависимости отключены (плагина `google-services` в проекте нет, вход через VK). `google-services.json` остался в `app/` — можно удалить.
13. `maps/ClinicRepository` возвращает mock-данные клиник (демо), но их адреса геокодируются через `Geocoder` — реальный поиск по Places API требует ключа Google.
15. **Авторизация VK**: ошибка «Invalid request: application is blocked» приходит от сервера VK — приложение 51420569 должно быть опубликовано (или тестовый аккаунт добавлен в разработчики), а в его настройках Android должны совпадать пакет `com.smock210.mypet` и SHA-1 подписи. Точные значения показывает экран «Диагностика VK».
14. ~~Тест миграции Room не написан~~ — **исправлено**: `MigrationTest.kt` (3 теста) прогоняет `MIGRATION_4_5` и `MIGRATION_5_6` вручную через `SupportSQLiteOpenHelper`, без `exportSchema = true`.

---

## 💡 Разработческие конвенции (выводы)

- **Именование**: Котлин-стиль `camelCase` для функций/переменных, `PascalCase` для классов.
- **ViewBinding**: Используется в `MainLoginForm` и части фрагментов (`First2Fragment`, `Second2Fragment`).
- **RecyclerView**: Адаптеры используют внутренний интерфейс колбэков (`OnItemClickListener`, `OnLongClickListener`).
- **Тестирование**: Минимальное — `ExampleUnitTest.kt` (JUnit) и `ExampleInstrumentedTest.kt` (Espresso).
- **Git**: Игнорируются файлы `.iml`, `.gradle`, `.idea/`, `build/`, `local.properties`.

---

## 🛠 Использование GIGACODE.md

Данный файл является контекстом для GigaCode-агента при последующих интеракциях с проектом. Агент использует его для:

- Понимания архитектуры и структуры проекта
- Выбора корректных команд сборки
- Следования существующим конвенциям при модификации кода
- Определения точек интеграции (навигация, VK, Firebase, Room, Picasso)

---

*Сгенерировано: 25 июня 2026 г.*

## GigaCode Added Memories
- Проект MyPet (LifePet) — дневник питомца. Полный функционал: аутентификация (VK + Firebase), питомцы (Room + CRUD), прививки (Vaccination), записи к ветеринару (VetVisit), календарь (CalendarContract), свайп-удаление, DataStore, Splash Screen, тесты (DatabaseTest + NavigationTest), 2 nav-графа. Все 10 задач по health-функционалу выполнены.
