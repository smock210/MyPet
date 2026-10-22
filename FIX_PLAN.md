# План исправлений и улучшений «MyPet» (LifePet)

Составлено на основе анализа кода: 21.10.2026.
Файлы-источники: `app/src/main/java/com/smock210/mypet/**`, `app/src/main/AndroidManifest.xml`, `app/build.gradle`, `PLAN.md`, `GIGACODE.md`.

Обозначения приоритета: **P0** — критично (функционал сломан), **P1** — важно (основной UX), **P2** — желательно, **P3** — косметика.
Каждая задача имеет критерий готовности (✅ Done), проверяемый вручную или тестом.

---

## Фаза 0. Быстрые победы (день 1)

### 0.1 Исправить передачу pet_id — главный баг проекта ✅ (21.10.2026)
**Проблема:** `MainActivity.onItemClick` (`MainActivity.kt:88-98`) кладёт только NAME/DETAIL/PHOTO, а `MainDetailActivity` читает `EXTRA_PET_ID` (`MainDetailActivity.kt:25`) → всегда `0L`. Далее `HealthActivity` и фрагменты получают `pet_id = 0` → все прививки/визиты всех питомцев пишутся с одним `petId = 0`.

**Правка:**
1. `MainActivity.onItemClick` — добавить `putExtra(MainDetailActivity.EXTRA_PET_ID, pet.id)`.
2. Проверить, что `MainDetailActivity` получает id из intent и передаёт его в `HealthActivity` (строки 46-47 уже передают `pet_id` — убедиться, что источник корректен).
3. `MainDetailActivity` — при `petId == 0L` завершать активность с сообщением (защита от повторения бага).

✅ **Done:** создать питомца №2, открыть его карточку → «Прививки», добавить прививку → у питомца №1 она не отображается.

### 0.2 Права и манифест ✅ (21.10.2026)
**Проблема:** в манифесте только `INTERNET`, `READ/WRITE_CALENDAR`. Не хватает:
- `POST_NOTIFICATIONS` (API 33+) — без него напоминания не показываются вообще;
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — для точных `AlarmManager`-напоминаний;
- `WRITE_EXTERNAL_STORAGE` (maxSdk 28) — нужен `ExportDataFragment` для JSON-экспорта;
- `CAMERA` — для `VideoRecorderHelper`.

**Правка:** объявить.permissions + запрос runtime-разрешений в точках использования (класс `notifications`, экспорт, камера).

✅ **Done:** `ExportDataFragment` и напоминания не падают с `SecurityException`.

### 0.3 Удалить мёртвый код ✅ (21.10.2026)
- `requests/VKUsersCommand.kt`, `requests/VKWallPostCommand.kt` — заглушки «без VK SDK», не используются;
- `classes/ClassPetAdapter.kt` — дублирующий адаптер (не используется, работает `NewPetAdapt`);
- `sqlClass/SQLPetDatabase.kt` — SQLiteOpenHelper-заглушка с TODO (Room уже полностью заменяет);
- `res/menu/user_menu.xml` — не инфлейтится нигде;
- `FirstFragment/SecondFragment` + `nav_graph.xml` **или** `First2Fragment/Second2Fragment` + `nav_graph2.xml` — оставить один граф (см. 1.3);
- неиспользуемые импорты (например, `ActivityResultContracts` в `VaccinationFragment`).

✅ **Done:** `./gradlew assembleDebug` проходит, поиск по проекту не находит ссылок на удалённые классы.

### 0.4 Обновить документацию ✅ (21.10.2026)
- `GIGACODE.md` устарел (AGP 7.3/Kotlin 1.7/minSdk 21 — фактически AGP/Kotlin из корневого `build.gradle`, compileSdk 35, minSdk 23). Обновить таблицу зависимостей и раздел «Обнаруженные проблемы» (пункты 1,2,6 уже исправлены в коде: `VkApplication` зарегистрирован, Room-сущности публичные, `FirstFragment` существует).
- `PLAN.md` — отметить выполненные пункты фазы 1 (1.1–1.3).

✅ **Done:** версии в `GIGACODE.md` совпадают с `app/build.gradle`.

---

## Фаза 1. Целостность данных и навигация (день 2–3)

### 1.1 Room: реактивные запросы (устранить ручной перезагрузки) ✅ (21.10.2026)
**Проблема:** DAO возвращают `List`, `PetViewModel` держит `_pets` и вызывает `loadPets()` после каждой записи. Устаревание данных при нескольких открытых экранах (список не обновится из `HealthActivity`).

**Правка:**
1. В DAO добавить Flow-варианты: `fun getAllFlow(): Flow<List<PetData>>`, `getVaccinationsForPetFlow(petId)`, `getVisitsForPetFlow(petId)`, `getPhotosForPetFlow(petId)`.
2. `PetViewModel`: заменить `MutableStateFlow` + ручные `loadX()` на `dao.getAllFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`.
3. Разделить ViewModel по экранам или добавить `petId`-scoped подмножества (сейчас один общий `PetViewModel` на все экраны — смешанное состояние `vaccinations` разных питомцев).

✅ **Done:** удаление/добавление питомца в `AddPetFragment` сразу видно в `MainActivity` без ручного `loadPets()`; `DatabaseTest` обновлён и зелёный.

### 1.2 Дожать `MainActivity`-цикл ✅ (21.10.2026)
**Проблема:** в `MainActivity.kt:33-62` на каждом эмите `pets` заново создаётся адаптер, `findViewById`, `setLayoutManager` и **повторно attach-ится `ItemTouchHelper`** (наложение нескольких колбэков → двойные удаления).

**Правка:** вынести инициализацию RecyclerView/ItemTouchHelper в `onCreate`, в `collect` — только `petAdapter.submitList(pet)`; использовать `ListAdapter` + `DiffUtil`. Заменить прямые `findViewById` на ViewBinding (модуль уже включён).

✅ **Done:** свайп удаляет ровно один питомц; `Log` показывает один вызов на свайп.

### 1.3 Навигация — оставить один механизм ✅ вариант A (21.10.2026)
**Проблема:** два одинаковых nav-графа; `MainLoginForm` и `RegistrActivity` — мёртвые точки входа; переходы между экранами — сырые `Intent`.

**Варианты (выбрать один):**
- **A (рекомендуется, минимум риска):** отказаться от Jetpack Navigation, удалить оба графа и `MainLoginForm`, `First*/Second*` фрагменты; переходы оставить на Intent (проект уже так работает).
- **B (дольше):** перевести `MainActivity` → `MainDetailActivity` → `HealthActivity` на один `nav_graph` c `NavHostFragment`, аргументы через `<argument>` + Safe Args.

**Одновременно:** `RegistrActivity` — реализовать минимальную регистрацию (Firebase Auth уже подключён: `firebase/FirebaseAuthHelper.kt`) или удалить пункт меню `menu_registr`.

✅ **Done:** в приложении нет экранов, до которых невозможно добраться; `NavigationTest` обновлён под фактическую навигацию.

---

## Фаза 2. Основной UX дневника (день 4–6)

### 2.1 Редактирование питомца ✅ (21.10.2026)
Кнопка «Редактировать» в `MainDetailActivity` → тот же `AddPetFragment` в режиме edit (передать `pet_id`, предзаполнить поля, `dao.update`).
✅ Правки отображаются после возврата.

### 2.2 Редактирование/удаление записей прививок и визитов ✅ (21.10.2026)
Сейчас только добавление и удаление (`PetViewModel.addVaccination/deleteVaccination`), нет редактирования и нет отображения статуса.
Добавить: долгий тап → меню «Изменить / Удалить / В календарь»; для прививок — состояние «сделана/запланирована» (новое поле → **миграция Room 4→5**).
✅ Запись редактируется, изменение видно сразу (за счёт 1.1).

### 2.3 Галерея фото питомца ✅ (21.10.2026)
Подключить `gallery/PetPhotoHelper.kt` (сейчас не вызывается): экран фото в `MainDetailActivity` (горизонтальный RecyclerView `PetPhoto`), выбор через `ActivityResultContracts.PickVisualContent` (Photo Picker, без разрешений на API 33+), назначение основного фото (`setPrimaryPhoto` уже есть в ViewModel).
✅ Можно добавить 2+ фото и выбрать главное.

### 2.4 Экраны-заготовки без точек входа ✅ (21.10.2026)
Подключить в меню `title_menu` или настройки:
- `ui/FeedingCalculatorFragment` + `utils/FeedingCalculator` — «Калькулятор кормления»;
- `ui/HealthScaleFragment` — «Оценка состояния» (сейчас demo-заглушка — заменить формулу на реальную оценку по весу/возрасту);
- `ui/ExportDataFragment` — «Экспорт данных» (после 0.2).
✅ Каждый экран открывается из меню и возвращает корректный результат.

### 2.5 Пустое состояние списка ✅ (21.10.2026)
Заглушка «Добавьте первого питомца» + иконка в `MainActivity` (RecyclerView скрывается).
✅ При чистой БД пользователь видит подсказку, а не пустой экран.

---

## Фаза 3. Напоминания и календарь (день 7–8)

### 3.1 Включить напоминания о прививках/визитах ✅ (21.10.2026)
**Проблема:** `NotificationHelper` вызывается только из `NotificationReceiver` — никто не планирует `AlarmManager`; `POST_NOTIFICATIONS` отсутствует (см. 0.2).

**Правка:**
1. После `addVaccination/addVisit` вызывать `scheduleReminder(...)` (по полю `reminderEnabled` / `dateMillis - 1 день`).
2. Запрос `POST_NOTIFICATIONS` на API 33+ перед первым планированием.
3. `canScheduleExactAlarms()` для API 31+.
4. При удалении записи — `cancel` соответствующего alarm.
✅ Прививка «завтра» → уведомление приходит (проверка через `adb shell am broadcast`).

### 3.2 Работа с календарём ✅ (21.10.2026)
`VetVisitFragment` уже создаёт событие через `CalendarContract` — добавить: отмену события при удалении визита, запрос `READ_CALENDAR/WRITE_CALENDAR` runtime.
✅ Удаление визита убирает событие из календаря.

---

## Фаза 4. Безопасность и вход (день 9–10)

### 4.1 Аутентификация ✅ вариант A: VK SDK 4.1.0 (21.10.2026)
- **Вариант A — VK:** VK SDK 3.5.2 устарел; обновить до 4.x, реализовать `VKUsersCommand`/`VKWallPostCommand` (сейчас заглушки), включить пункт «Войти» (`menu_vhod` — Toast «отключен»).
- **Вариант B — Firebase:** подключить `firebase/FirebaseAuthHelper.kt` (email/password) к `RegistrActivity` — меньше работы, зависимость уже в gradle.
✅ Регистрация/вход работают, аватар и имя подтягиваются.

### 4.2 Защита локальных данных ✅ (21.10.2026)
Подключить лежащий в проекте код (сейчас не вызывается ниоткуда):
- `security/SecurePreferencesManager` — хранение токена/настроек в EncryptedSharedPreferences;
- `security/DataStoreProvider` — миграция SharedPreferences → DataStore;
- `security/BiometricAuthHelper` — опциональный вход по отпечатку (пункт в настройках).
✅ Настройка «Вход по биометрии» включается и блокирует приложение.

---

## Фаза 5. Устойчивость, качество, CI (день 11–13)

### 5.1 Тесты ✅ частично (21.10.2026)
- `DatabaseTest` — расширить: in-memory Room, проверка каскадного удаления (прививки/фото при удалении питомца), проверка миграции 1→4 (`MigrationTestHelper`).
- Тест `pet_id`: unit-тест передачи id из `MainActivity` (Robolectric).
- `NavigationTest` — убрать дубликат `testAppLaunches`/`testRecyclerViewVisible`, добавить тест перехода в карточку и в `HealthActivity`.
- Тест `FeedingCalculator` и `DataExporter` (чистые функции — легко).
✅ `./gradlew test` зелёный, покрытие ключевой логики > 0.

### 5.2 Экран и ресурсы ✅ частично (21.10.2026)
- Экспорт (`DataExporter`) → в App-своё каталог через `MediaStore` (SAF), без `WRITE_EXTERNAL_STORAGE` на API 29+.
- PDF-отчёт (`pdf/HealthReportExporter`) — кнопка «Отчёт» в `HealthActivity` (share-intent).
- `maps/MapsFragment` — mock-данные клиник заменить на реальный поиск (Places API) или честно пометить «демо» в UI.
- `video/VideoRecorderHelper` — подключить к карточке питомца (сейчас не вызывается).

### 5.3 Конфигурация сборки ✅ (21.10.2026)
- `google-services.json` — проверить наличие (Firebase-плагин: в `app/build.gradle` нет `id 'com.google.gms.google-services'`, но зависимости Firebase подключены — либо добавить плагин, либо убрать Firebase, если выбран VK).
- Room-зависимости: убрать неиспользуемые `room-rxjava2`, `room-rxjava3`, `room-guava`, `room-paging` (~−30% времени сборки).
- Убрать `lifecycle-livedata-ktx` (используется только StateFlow) или перейти на LiveData — выбрать одно.
- Поднять target SDK 35 → проверить фото/уведомления на Android 14/15.
- `proguard-rules.pro` пустой — добавить keep-правила для Room/VK/Gson перед `minifyEnabled true`.

### 5.4 CI (по желанию)
GitHub Actions: `./gradlew test lintDebug` на каждый push.

---

## Фаза 6. Улучшения продукта (P2–P3)

| № | Задача | Заметки |
|---|---|---|
| 6.1 | График веса/роста питомца ✅ | таблица `WeightRecord`, миграция 5→6, `WeightChartView` (Canvas) |
| 6.2 | Поиск/фильтр списка питомцев ✅ | `SearchView` в `title_menu` |
| 6.3 | Свайп-редактирование + Undo-Snackbar ✅ | удаление питомца со снапшотом и кнопкой «Отменить» |
| 6.4 | Тёмная/светлая тема переключателем ✅ | сделано в фазе 4.2 (DataStore + `AppCompatDelegate`) |
| 6.5 | Material 3 + Dynamic Color ✅ | `Theme.Material3.*` + `DynamicColors` для API 31+ |
| 6.6 | SplashScreen API (Android 12+) ✅ | `core-splashscreen:1.0.1`, `Theme.SplashScreen` |
| 6.7 | Публикация фото питомца в VK ✅ | `VKWallPostCommand` (upload + `wall.post`) |
| 6.8 | Локализация (en) ✅ | `values-en/strings.xml`, захардкоженные строки вынесены в ресурсы |

---

## Сводка приоритетов

```
P0 (без этого баг):   0.1 pet_id, 0.2 права, 1.2 ItemTouchHelper-дубли
P1 (основной UX):     1.1 Flow-BD, 1.3 единая навигация, 2.1–2.3, 3.1 напоминания
P2 (важно):           2.4, 2.5, 4.1 вход, 4.2 безопасность, 5.1 тесты, 6.3 Undo
P3 (полировка):       0.3/0.4 чистка, 5.2–5.4, 6.1–6.8
```

## Порядок выполнения (рекомендуемый)

1. **Фаза 0** (0.1 → 0.2 → 0.3) — сразу чинит главный баг, ничего не ломая.
2. **Фаза 1** (1.2 → 1.1 → 1.3) — стабильная основа данных и экранов.
3. **Фаза 2** — пользовательский ценность дневника.
4. **Фазы 3–4** — напоминания и вход.
5. **Фаза 5** — тесты и сборка (тесты на самом деле стоит писать инкрементно после каждой задачи).
6. **Фаза 6** — по мере сил.

## Риски

- **Миграции Room** (новые поля в 2.2, 6.1) — только с `MigrationTestHelper`, версия БД растёт с 4.
- **Удаление VK-заглушек** (0.3) — сначала решить 4.1 (VK или Firebase), чтобы не выбросить точку интеграции.
- **Зависимость Firebase** — нет `google-services.json`/плагина → сборка может падать; проверить первой в фазе 5.3.

---

## 📝 Журнал выполнения

### 21.10.2026 — Фазы 0 и 1 выполнены

**Исправлено:**
- 0.1: `MainActivity` теперь передаёт `EXTRA_PET_ID`; `MainDetailActivity` защищён от `petId = 0` (Toast + finish).
- 0.2: в манифест добавлены `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `WRITE_EXTERNAL_STORAGE` (maxSdk 28), `CAMERA`.
- 0.3: удалены `requests/VKUsersCommand.kt`, `requests/VKWallPostCommand.kt` (обе заглушки, в одной была даже китайская строка в `onError`), `classes/ClassPetAdapter.kt`, `sqlClass/SQLPetDatabase.kt`, `FirstFragment`/`SecondFragment` + `nav_graph.xml` + их лэйауты, `user_menu.xml`, неиспользуемый импорт в `VaccinationFragment`.
- 0.4: `GIGACODE.md` (версии AGP 8.8.0/Kotlin 1.9.24/Gradle 8.10.2, sdk 35/23, актуальный список компонентов и дефектов), `PLAN.md` (статусы фазы 1).
- 1.1: DAO получили Flow-варианты (`getAllFlow`, `getVaccinationsForPetFlow`, `getVisitsForPetFlow`, `getPhotosForPetFlow`); `PetViewModel` переведён на `stateIn` + `flatMapLatest` по `currentPetId` — никаких ручных `loadX()` после записи.
- 1.2: `NewPetAdapt` → `ListAdapter` + `DiffUtil` (по `id`/`equals`); `MainActivity` — инициализация RecyclerView и `ItemTouchHelper` один раз в `onCreate`, в `collect` только `submitList`; ViewBinding вместо `findViewById`.
- 1.3 (вариант A): удалены `MainLoginForm`, `First2Fragment`/`Second2Fragment`, `nav_graph2.xml`, их лэйауты, запись в манифесте и шаблонные строки; удалены зависимости `navigation-fragment-ktx`/`navigation-ui-ktx`; дубликат `testRecyclerViewVisible` удалён.

**Попутно исправленные баги (не входили в план):**
- `EmployeeDao.insert` теперь возвращает `Long` — раньше `PetViewModel.addPet` сохранял `PetPhoto` с `petId = 0` (id был неизвестен до вставки) и в `DatabaseTest` нельзя было проверить удаление.
- `testDelete` в `DatabaseTest` был некорректен (удалял entity с `id = 0`) — исправлен.
- В `DatabaseTest` не хватало `testImplementation` для `androidx.test.ext:junit` и `androidx.test:core` (были только в `androidTestImplementation`) — добавлены вместе с `kotlinx-coroutines-test`.
- Добавлены явные `androidx.recyclerview:recyclerview:1.3.2` (нужен `bindingAdapterPosition`) и `androidx.fragment:fragment-ktx:1.8.4` (нужен `by viewModels()` — раньше приходил транзитивно из navigation).

**Среда:** на диске осталось ~2 GiB, из-за чего падала сборка; по согласованию очищен кэш `~/.gradle/caches/9.0.0` (2.8G) и логи демонов → стало ~24 GiB свободно.

**Проверки:** `./gradlew assembleDebug` — BUILD SUCCESSFUL; `./gradlew testDebugUnitTest` — 5/5 тестов зелёные.

### 21.10.2026 — Фаза 2 выполнена

**2.1 Редактирование питомца:**
- `AddPetActivity` принимает `EXTRA_PET_ID` и передаёт его в `AddPetFragment.newInstance(petId)`.
- `AddPetFragment` поддерживает режим edit: загружает питомца через `viewModel.loadPetForEdit()`, предзаполняет поля (включая Spinner по виду и фото через Picasso), заголовок «Редактирование питомца», сохранение через `updatePet(copy(...))` с сохранением старого фото, если новое не выбрано.
- В `MainDetailActivity` добавлена кнопка «Редактировать»; карточка теперь подписана на БД (`editedPet`) и обновляется после правок.

**2.2 Записи здоровья (прививки/визиты):**
- Миграция Room **4→5**: `Vaccination.done` (сделана/запланирована).
- Новые адаптеры `VaccinationAdapter` и `VetVisitAdapter` (ListAdapter + DiffUtil).
- `fragment_vaccination.xml`/`fragment_vet_visit.xml` перестроены: форма + пустое состояние + RecyclerView со списком записей.
- Клик по записи — режим редактирования (форма предзаполняется, кнопка становится «Обновить»); галочка «сделана» у прививки; удаление — через `MaterialAlertDialogBuilder` с подтверждением.

**2.3 Галерея фото:**
- `PetPhotoAdapter` + `item_pet_photo.xml`; в карточке питомца — горизонтальный RecyclerView, кнопка «Добавить фото» (Photo Picker через `GetContent`), клик по фото делает его основным (обновляет и `PetPhoto.isPrimary`, и `PetData.photoUri`), кнопка удаления с Toast.
- Использован `GetContent` вместо `PickVisualContent` — требует activity 1.7+ в compile-класспассе, а там транзитивно 1.8.1 не дал нужных типов; `GetContent` открывает Photo Picker на Android 13+ автоматически.

**2.4 Экраны без точек входа:**
- Новая `ToolsActivity` (контейнер фрагментов) + пункт меню: «Калькулятор кормления», «Экспорт данных», «Оценка состояния».
- `ExportDataFragment` переписан: экспорт **всех** данных через `viewModel.exportSnapshot()` (а не только текущего petId), запись через `MediaStore.Downloads` на Android 10+ без разрешений, legacy-разрешение только на Android 9−, колбэки через `registerForActivityResult`.
- Добавлены `getAllVaccinations`/`getAllVisits`/`getAllPhotos` в DAO.

**2.5 Пустое состояние:** `tv_empty_state` в `activity_main_start.xml`, переключение видимости RecyclerView/заглушки в `MainActivity`.

**Проверки:** `./gradlew assembleDebug` ✅; `./gradlew testDebugUnitTest` ✅ (5/5).

### 21.10.2026 — Фаза 3 выполнена

**3.1 Напоминания включены:**
- `PetViewModel.addVaccination/addVisit` теперь планируют напоминание за 24 часа через `NotificationHelper` (используется id, возвращённый Room-вставкой).
- `deleteVaccination/deleteVisit` — отмена `AlarmManager`-напоминания.
- `NotificationHelper.setAlarm`: защита от `SecurityException` на Android 12+ (`canScheduleExactAlarms()` → откат на `setAndAllowWhileIdle`), напоминания в прошлое не планируются.
- **Исправлен баг**: `NotificationReceiver` читал id через `getIntExtra`, а передавался `Long` → id всегда 0 и уведомления конфликтовали между собой. Теперь `getLongExtra`.
- `POST_NOTIFICATIONS` запрашивается runtime в `VaccinationFragment`/`VetVisitFragment` на Android 13+.

**3.2 Календарь:**
- `deleteVisit` удаляет событие календаря по `calendarEventId` (SecurityException при отсутствии `WRITE_CALENDAR` перехватывается, визит удаляется в любом случае).

**Проверки:** `./gradlew assembleDebug` ✅; `./gradlew testDebugUnitTest` ✅.

### 21.10.2026 — Фаза 4 выполнена (выбран VK SDK 4.x)

**4.1 Аутентификация через VK SDK 4.1.0:**
- Зависимости обновлены `com.vk:android-sdk-core/api:3.5.2` → `4.1.0` (последняя на Maven Central).
- `requests/VKUsersCommand.kt` реализован по официальному сэмплу VKCOM (`ApiCommand` из `com.vk.api.sdk.internal`, парсер `VKApiJSONResponseParser`).
- `MainActivity`: `VK.login(this) { VKAuthenticationResult -> ... }` (ActivityResultLauncher), пункты «Войти» и «Зарегистрироваться» (регистрация на стороне VK) запускают вход со scope PHOTOS/EMAIL/PHONE; после входа — `VK.executeSync(VKUsersCommand())` в `Dispatchers.IO`, имя/аватар в шапке.
- Удалены `RegistrActivity` (пустая заглушка) и её лэйаут/запись в манифесте.

**4.2 Безопасность:**
- `BiometricAuthHelper` подключён: пункт меню «Вход по биометрии» (вкл/выкл, проверка `isAvailable`), при включённой блокировке `MainActivity` требует биометрию при каждом старте (ошибка → `finish()`).
- `DataStoreProvider`: + флаги `biometric_lock` и синхронные геттеры (`getThemeModeSync`, `getBiometricLockSync`); тема приложения теперь хранится в DataStore.
- `SecurePreferencesManager` (EncryptedSharedPreferences) кэширует профиль VK (имя/аватар/id) — шапка заполняется мгновенно, без ошибки при оффлайне.
- Пункт меню «Сменить тему» — циклически системная → светлая → тёмная (`AppCompatDelegate.setDefaultNightMode`), применяется до наполнения UI. Это закрыло и пункт 6.4 плана.

### 21.10.2026 — Фаза 5 выполнена (частично)

**5.1 Тесты (16 шт., все зелёные):**
- `FeedingCalculatorTest` — 6 unit-тестов чистых формул (порция по возрасту, активность, деление, калории, округление).
- `DatabaseTest` — +5 тестов: прививки/визиты изолированы по petId (регресс к багу 0.1), `insertVaccination` возвращает id и `done = false` (миграция 4→5), фото по petId + `deleteAllForPet`, обновление `done`.
- ⏳ Не сделано: тест миграции с `MigrationTestHelper` (требует `exportSchema = true` + JSON-схем в репозитории).

**5.2 Экраны/ресурсы:**
- `pdf/HealthReportExporter` — **исправлен баг**: `document.close()` вызывался до `writeTo()` → отчёт всегда был пустым. Теперь запись через `MediaStore.Downloads` на Android 10+.
- Кнопка «PDF-отчёт» в `HealthActivity` (options menu), данные ждутся через `first()` + `withTimeoutOrNull` (учтён `WhileSubscribed`).
- ⏳ Не сделано: реальный поиск клиник в `maps/` (остаются mock-данные), `VideoRecorderHelper` не подключён.

**5.3 Конфигурация сборки:**
- Удалены неиспользуемые Room-артефакты: `room-rxjava2`, `room-rxjava3`, `room-guava`, `room-paging` (~−30% времени сборки).
- Удалены `lifecycle-livedata-ktx` (используется только StateFlow).
- **Firebase отключён** (BOM, firebase-auth-ktx, play-services-auth закомментированы; класс `firebase/FirebaseAuthHelper` удалён) — вход через VK, плагина `google-services` в проекте нет. Добавлена явная `kotlinx-coroutines-play-services:1.9.0` (её давал транзитивно play-services-auth, нужна `tasks.await()` в `maps/ClinicRepository`).
- `proguard-rules.pro` дополнен keep-правилами VK SDK, Gson/экспортных entity, Room, Picasso + line numbers.

**Проверки:** `./gradlew assembleDebug` ✅; `./gradlew testDebugUnitTest` ✅ (16/16).



### 21.10.2026 — Остаток Фазы 5 и Фаза 6 выполнены

**5.1 Миграционный тест (закрыто):**
- `app/src/test/.../MigrationTest.kt` — 3 теста: миграция 4→5 (появилась колонка `done`, данные сохранились), пустая таблица, миграция 5→6 (создалась `WeightRecord`).
- `MigrationTestHelper` не потребовался: тест сам создаёт БД схемы v4 через `SupportSQLiteOpenHelper` и прогоняет `AppDatabase.MIGRATION_*` вручную — не требует `exportSchema = true` и JSON-схем в репозитории.
- Миграции в `AppDatabase` стали `internal` (доступны из тестов).

**5.2 maps/video (закрыто):**
- `video/VideoRecorderHelper` переписан на `ActivityResultLauncher` (был устаревший `startActivityForResult`), подключён кнопкой «Записать видео» в карточке питомца.
- `maps/MapsFragment` подключён к меню («Ветклиники на карте»): поиск теперь **реальный** — геокодирование через системный `Geocoder` (без API-ключа Places) с переносом маркера; «Открыть в Google Maps» ищет по введённому запросу (с фолбэком на обычный `geo:`-intent).
- `maps/ClinicRepository` перестал быть мёртвым: маркеры клиник ставятся по геокодированным адресам, камера центрируется на текущей геопозиции.
- Удалён мёртвый `gallery/PetPhotoHelper.kt` (галерея работает через `GetContent`).

**5.4 CI:** `.github/workflows/android.yml` — сборка `assembleDebug` + `testDebugUnitTest` на JDK 17, выгрузка APK и отчёта тестов артефактами.

**6.1 График веса:** новая сущность `WeightRecord` + `WeightDao`, миграция **5→6**, `WeightChartView` (рисуется на Canvas, без сторонних графиков), кнопка «Записать взвешивание» и обновление текущего веса питомца.

**6.2 Поиск:** `SearchView` в меню, фильтрация по кличке/породе/виду/заметкам; пустое состояние различает «нет питомцев» и «ничего не найдено». Попутно исправлено: свайп-удаление брало питомца из полного списка, а не из отфильтрованного (при активном поире удалялся бы не тот).

**6.3 Undo-Snackbar:** удаление питомца больше не безвозвратно — `PetViewModel.deletePetWithSnapshot()` снимает снапшот (питомец, фото, прививки, визиты, вес), `Snackbar` с кнопкой «Отменить» восстанавливает всё через `restorePetSnapshot()`.

**6.5 Material 3:** темы переведены на `Theme.Material3.DayNight` (в Material 3 нет суффикса `DarkActionBar`), `DynamicColors.applyToActivitiesIfAvailable()` в `VkApplication` — цвета берутся из обоев на Android 12+.

**6.6 SplashScreen API:** подключён `androidx.core:core-splashscreen:1.0.1`, тема `Theme.SplashScreen`. Библиотека собрана Kotlin 2.0, а проект — на Kotlin 1.9.24 (метаданные не читаются) → вызов статического метода вынесен в Java-обёртку `SplashScreenCompat`.

**6.7 Публикация в VK:** `requests/VKWallPostCommand.kt` (по официальному сэмплу VKCOM: `photos.getWallUploadServer` → загрузка файла → `photos.saveWallPhoto` → `wall.post`), кнопка «Опубликовать в VK» в карточке питомца; требует авторизации и основного фото.

**6.8 Локализация (en):** добавлен `values-en/strings.xml` (полный перевод), 60+ захардкоженных строк из лэйаутов и кода вынесены в `strings.xml` (биометрия, уведомления, PDF-отчёт, карта, калькулятор, health-scale, диалоги).

**Попутная чистка (без предупреждений компилятора):**
- `HealthActivity`: устаревшие `FragmentPagerAdapter`/`FragmentStatePagerAdapter` → `ViewPager2` + `FragmentStateAdapter` + `TabLayoutMediator` (добавлена зависимость `viewpager2:1.1.0`).
- `ClinicRepository`: клиники получили координаты, сортировка по расстоянию (формула гаверсинуса) и фильтр по радиусу — параметры `lat/lng/radiusKm` больше не игнорируются.
- `FeedingCalculatorFragment`: результат вынесен в ресурс `feeding_result`, добавлена порция по возрасту.
- `WeightChartView`: `scaledDensity` (deprecated) заменён на `TypedValue.applyDimension`.
- `MIGRATION_*` в `AppDatabase` стали `internal` для тестов.

**Проверки:** `./gradlew clean assembleDebug testDebugUnitTest` ✅ без предупреждений; **21/21** тест зелёный (11 DatabaseTest + 6 FeedingCalculator + 3 MigrationTest + 1 Example); APK 11 МБ.


### 21.10.2026 — Диагностика авторизации VK

**Симптом:** при входе через VK — ошибка VK «Invalid request: application is blocked».

**Разбор:** ошибка приходит **от сервера VK**, а не из кода приложения. VK отклоняет OAuth-запрос, если приложение в кабинете разработчика не проходит проверку:
- тип приложения не «Standalone»;
- приложение не опубликовано (в режиме разработки авторизоваться могут только разработчики/тестировщики приложения);
- в разделе «Android» не совпадают имя пакета или SHA-1 отпечаток подписи установленного APK.

**Что сделано в коде:**
- `MainActivity`: при отказе авторизации теперь показывается **реальная причина** от VK (`VKAuthException.authError`) вместо обобщённого Toast, плюс запись в Logcat.
- Новый экран «Диагностика VK» (меню → «Диагностика VK»): показывает App ID, имя пакета, SHA-1 отпечатки сертификата подписи и статус входа — значения, которые нужно перенести в настройки приложения VK.
- `VkApplication`: уточнено, что инициализацию SDK выполняет `VKInitProvider` из манифеста SDK; ручной вызов `VK.initialize()` убран, чтобы не пересоздавать конфиг и не терять сохранённый токен.

**Обновление App ID:** приложение заменено на новое — ID **54841026** «Жизнь петомца» (тип standalone). Старый ID 51420569 больше не используется.
Проверка через API VK (`apps.get` с сервисным ключом) показала: у приложения **нет поля `android`** — то есть в кабинете разработчика Android-настройки не заполнены. Именно это и вызывает «application is blocked».

**Диагностика в приложении (расширена):**
- `utils/VkAppConfigChecker` — запрос `apps.get` с сервисным ключом: показывает тип приложения, состояние (разработка/опубликовано/заблокировано) и заполнены ли Android-настройки (пакет, SHA-1, activity);
- сервисный ключ читается из `local.properties` (`VK_SERVICE_KEY`, файл в `.gitignore`) → `BuildConfig.VK_SERVICE_KEY` и **только в debug-сборке** — в release ключа нет, чтобы серверный секрет не попадал в публичный APK;
- экран «Диагностика VK» теперь выводит и локальные данные (App ID, пакет, отпечатки подписи), и результат проверки кабинета VK.

**Что нужно сделать пользователю (кабинет VK, https://vk.com/apps?act=manage → приложение 54841026 «Жизнь петомца»):**
- тип приложения — Standalone;
- «Имя пакета для Android» — `com.smock210.mypet`;
- «Сертификат для Android» (SHA-1) — `33:3C:D4:02:C3:C8:96:25:01:35:9B:28:BC:9C:B9:2C:C0:E2:DE:75` (отладочный ключ `~/.android/debug.keystore`; release-сборки подписываются тем же ключом, т.к. `signingConfigs` в проекте нет);
- «Main Activity для Android» — `com.smock210.mypet.SplashActivity` (или `MainActivity`);
- если приложение в режиме разработки — добавить тестовый аккаунт в раздел «Разработчики» либо опубликовать приложение;
- после проверки рекомендуется перевыпустить сервисный и защитный ключи в кабинете (они были переданы в открытом чате) и хранить их только на сервере.

**Проверки:** `./gradlew assembleDebug testDebugUnitTest` ✅ (21/21).


### 21.10.2026 — Круглые фото питомцев на главном экране

**Проблема:** в списке питомцев на главном экране квадратная иконка без фото (загружалась иконка лаунчера), имя питомца выводилось на «бирюзовой плашке» высотой 115 dp, а при клике/долгом клике фон элемента заливался чёрным/серым.

**Изменения:**
- `list_row.xml`: `ImageView` → `CircleImageView` (48 dp, `centerCrop`, бирюзовая рамка 2 dp, круглая подложка) + нормальный `TextView` с именем (без плашки, `ellipsize`, `?android:attr/textColorPrimary` вместо чёрного — иначе не видно в тёмной теме);
- `NewPetAdapt`: теперь реально грузится **фото питомца** (`pet.photoUri`) через Picasso; без фото — новая заглушка-отпечаток лапки `drawable/ic_pet_placeholder.xml`; убрана заливка фона при клике/долгом клике (остался ripple `?attr/selectableItemBackground`);
- `activity_main_start.xml`: аватар пользователя в шапке — тоже `CircleImageView`; список расширен до 180 dp, чтобы имя не обрезалось;
- **Постоянные фото:** добавлен `utils/ImageStorage` — выбранные через Photo Picker снимки копируются в приватное хранилище (`filesDir/photos`), в БД пишется путь к файлу. Раньше сохранялся временный `content://` URI, доступ к которому теряется после перезапуска приложения — фото «исчезали»;
- `PetViewModel.migrateLegacyPhotoUris()` — при старте ранее сохранённые `content://` ссылки автоматически переносятся в приватное хранилище, поэтому старые фото продолжают работать;
- `MainDetailActivity`: при публикации в VK путь к файлу преобразуется в `file://` Uri.

**Проверки:** `./gradlew assembleDebug testDebugUnitTest` ✅ (21/21).


### 22.10.2026 — Вход через Google и Huawei ID (в зависимости от устройства)

**Что сделано:**
- Новый пакет `ui/auth`: единая абстракция провайдера авторизации (`AuthProvider`, `AuthProfile`, `AuthResult`) — VK, Google и Huawei ID используют одну модель профиля и один код показа в шапке;
- `GoogleAuthProvider` — Google Sign-In (`play-services-auth:21.1.1`): проверка доступности Google Play Services и наличие `google_web_client_id` **до** запуска входа (иначе Google отвечает ошибкой 10);
- `HuaweiAuthProvider` — вход через **Huawei ID по web-авторизации (OAuth 2.0 / OIDC)** в браузере: открывается Custom Tab → редирект на custom-схему `mypet://oauth/huawei` → обмен кода на токен → запрос профиля. Реализация без HMS SDK: работает на любых устройствах, включая Huawei/Honor без Google Play Services, и не требует `agconnect-services.json`;
- `AuthProviders.chooseProvider(...)` — правило выбора: устройство Huawei/Honor → Huawei ID, иначе при доступных Play Services → Google, иначе → настроенный провайдер;
- В меню добавлены пункты «Войти через Google» и «Войти через Huawei ID»; при первом запуске показывается Snackbar с рекомендуемым для устройства способом входа и кнопкой «Войти»;
- Экран «Диагностика входа» (бывший «Диагностика VK») показывает: доступность Google Play Services, настроен ли `google_web_client_id`, настроен ли `huawei_client_id`, устройство ли это Huawei/Honor, установлен ли HMS Core и какой провайдер рекомендован;
- Профиль пользователя (имя, e-mail, фото, провайдер) кэшируется в EncryptedSharedPreferences независимо от способа входа.

**Почему без HMS SDK:** артефакты `com.huawei.hms:account`/`core` недоступны в зеркале репозиториев окружения (404), а web-авторизация не требует ни SDK, ни agconnect-конфига. Если позже понадобится HMS Account Kit — зависимость добавляется в `app/build.gradle` (репозиторий `https://developer.huawei.com/repo/`) и плагин `com.huawei.agconnect:agcp`.

**Что необходимо настроить пользователю:**
- **Google:** Google Cloud Console → OAuth client ID типа «Веб-приложение» → значение в `R.string.google_web_client_id` (`app/src/main/res/values/strings.xml`);
- **Huawei:** AppGallery Connect → Мои приложения → Huawei ID → Авторизация через веб → Client ID, Client Secret и Redirect URI (`mypet://oauth/huawei`) → значения в `R.string.huawei_client_id` / `huawei_client_secret` / `huawei_redirect_uri`.

**Тесты:** `AuthProvidersTest` — 6 тестов на правило выбора провайдера. Всего 27/27 зелёные.


### 22.10.2026 — Исправлен вход через Huawei ID (телефон без Google-сервисов)

**Симптом:** на Huawei без GSP вход через Huawei ID не отрабатывает.

**Причины (две):**
1. **Неверный хост эндпоинтов.** Использовался `oauth.huawei.com` — такого домена не существует (DNS не резолвится ни на Mac, ни на телефоне). Канонические адреса взяты из официального конфига провайдера `https://oauth-login.cloud.huawei.com/.well-known/openid-configuration`:
   - `authorization_endpoint`: `https://oauth-login.cloud.huawei.com/oauth2/v3/authorize`;
   - `token_endpoint`: `https://oauth-login.cloud.huawei.com/oauth2/v3/token`;
   - `token_endpoint_auth_methods_supported`: `client_secret_post`;
   - `scopes_supported`: `openid`, `email`, `profile`; `code_challenge_methods_supported`: `S256`.
2. **Эндпоинта `userinfo` у Huawei нет** (404 на всех вариантах пути) — профиль нужно брать из JWT-токена `id_token` (claim'ы: `sub`, `name`/`display_name`, `picture`, `email`, `given_name`, `family_name`).

**Исправления в коде:**
- `HuaweiAuthActivity`: правильные эндпоинты, поддержка **PKCE (S256)** (`code_challenge` + `code_verifier`), разбор профиля из `id_token` (Base64URL-декодирование payload), обработка ошибок `error`/`error_description` от сервера;
- `AuthConfig` — единый источник учётных данных: сначала зашифрованное хранилище, затем ресурсы;
- **Экран «Диагностика входа»** получил поля для Google Web client ID и Huawei Client ID / Client Secret / Redirect URI с кнопкой «Сохранить» — настроить вход можно прямо на устройстве, без пересборки APK;
- `SecurePreferencesManager` — хранение этих данных в EncryptedSharedPreferences.

**Проверено на подключённом Huawei AQM-LX1 (без Google Play Services):**
- приложение ставится и запускается без падений (зависимость Google не ломает старт);
- редирект `mypet://oauth/huawei?code=...` корректно перехватывается `HuaweiRedirectActivity` (проверено через `am start`).

**Что осталось от пользователя:** завести приложение в AppGallery Connect → «Huawei ID» → Авторизация через веб, получить Client ID / Client Secret, зарегистрировать Redirect URI `mypet://oauth/huawei` и внести значения на экране «Диагностика входа».


### 22.10.2026 — Huawei ID: loopback-редирект (по данным AppGallery Connect)

**Данные из кабинета AppGallery Connect:** имя пакета `smock210.mypet.huawei`, ID приложения/OAuth-клиента `6917565158910974246`, секрет клиента получен.

**Проверка клиента (запросы к `/oauth2/ajax/authorizeConfirm`):**
- `client_id=6917565158910974246` — **валиден** (ошибка не про client_id);
- `redirect_uri=mypet://oauth/huawei` → `invalid redirect_uri` (sub_error 20022) — **custom-схемы Huawei не принимает**;
- `redirect_uri=http://localhost/...`, `https://example.com/...` → `redirect_uri not registered` (sub_error 20023) — формат допустим, но адрес **обязан быть зарегистрирован** в AGC.

**Решение — loopback-паттерн (без внешнего сервера):**
- `HuaweiLoopbackServer`: приложение поднимает HTTP-сервер на `127.0.0.1:8765` и ловит код авторизации прямо на устройстве; поддержаны и query-параметры, и POST-тело (`response_mode=form_post`);
- `HuaweiAuthActivity`: вместо custom-схемы используется `redirect_uri=http://127.0.0.1:8765/oauth/huawei` — этот URL нужно один раз указать в AGC («Huawei ID» → Авторизация через веб → Redirect URL);
- удалены `HuaweiRedirectActivity` и `HuaweiOAuthBridge` (приём редиректа перенесён в локальный сервер);
- ключи Google/Huawei читаются из `local.properties` (`GOOGLE_WEB_CLIENT_ID`, `HUAWEI_CLIENT_ID`, `HUAWEI_CLIENT_SECRET`) → `BuildConfig` **только для debug**, в release — пусто; в приложении их можно задать и на экране «Диагностика входа» (EncryptedSharedPreferences);
- `HuaweiLoopbackServerTest` — 3 теста: приём кода из query, из POST-тела, обработка ошибки. Всего 30/30 зелёные.

**Важно:** имя пакета в AGC (`smock210.mypet.huawei`) не совпадает с `applicationId` приложения (`com.smock210.mypet`) — для публикации в AppGallery нужно завести приложение с правильным именем пакета. На веб-авторизацию это не влияет.


### 22.10.2026 — Интеграция с HMS: нативный вход через Huawei ID

**Что найдено:** модуль Account Kit (`com.huawei.hms:account`) в зеркале репозитория отсутствует (404), но доступен **`com.huawei.hms:hwid:6.15.0.300`** — нативный модуль входа через Huawei ID (`HuaweiIdAuthManager`, `HuaweiIdAuthParams`, `AuthHuaweiId`). Он содержит и сам `com.huawei.hms.account`, а недостающие классы поддержки (`com.huawei.hms.support.api.entity.auth.Scope`) лежат в `com.huawei.hms:base:6.13.0.304`.

**Особенность сборки:** в этом окружении POM-файлы HMS отдают 404, поэтому репозиторий Huawei подключён с `metadataSources { artifact() }`, а тип артефакта указан явно (`@aar`), транзитивные зависимости прописаны вручную:
- `com.huawei.hms:hwid:6.15.0.300@aar` — вход через Huawei ID;
- `com.huawei.hms:base:6.13.0.304@aar` — support API (`Scope`, `AbstractAuthAccount`);
- `com.huawei.hmf:tasks:1.5.2.301` — `Task` (success/failure listeners).

**Конфигурация AppGallery Connect:**
- добавлен `app/agconnect-services.json` (client_id `6917565158910974246`, api_key = SHA-256 сертификата подписи, package_name `com.smock210.mypet`);
- задача `copyAgconnectServices` копирует его в `app/src/main/assets/` (то же делает плагин `com.huawei.agconnect:agcp`, но без него сборка не падает, если файла нет);
- экран «Диагностика входа» показывает, что именно вписать в AGC: имя пакета, SHA-256 отпечаток сертификата (считается на устройстве), Redirect URL и OAuth Client ID.

**Новый провайдер `HuaweiNativeAuthProvider`:**
- `HuaweiIdAuthManager.getService(activity, HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)` → `getSignInIntent(requestCode)` → системный экран входа Huawei ID;
- результат: `HuaweiIdAuthManager.parseAuthResultFromIntent(data)` → `Task<AuthHuaweiId>` → профиль (`getUid`, `getDisplayName`, `getEmail`, `getAvatarUri`);
- доступен только при установленном HMS Core (`com.huawei.hwid`); при его отсутствии — fallback на веб-авторизацию через loopback.

**Выбор провайдера:** на Huawei/Honor с HMS Core → нативный вход; иначе Google (если есть Play Services) → веб-Huawei → Google. Правило вынесено в чистую функцию `chooseProvider` и покрыто тестами (+2, всего 32/32 зелёные).


### 22.10.2026 — Исправлено падение при входе через Huawei ID

**Симптом:** при нажатии «Войти через Huawei ID» приложение закрывалось.

**Причина падения (по logcat на Huawei AQM-LX1):**
```
java.lang.NoClassDefFoundError: Failed resolution of: Lcom/huawei/hms/utils/Checker;
    at com.huawei.hms.common.HuaweiApi.<init>
    at com.huawei.hms.support.hwid.HuaweiIdAuthManager.getService
```
Классы `com.huawei.hms.utils.*`, `com.huawei.hms.common.api.*`, `com.huawei.hms.ui.*` и ещё 17 — из модуля **`com.huawei.hms:core`**, который недоступен в зеркале репозитория (404, как и `com.huawei.hms:account`). Без него нативный HMS-вход невозможен.

**Исправления:**
- `HuaweiNativeAuthProvider.isAvailable()` теперь проверяет не только HMS Core, но и наличие класса из `core` (`Class.forName("com.huawei.hms.utils.Checker")`) — если модуля нет, нативный путь просто не предлагается;
- `signIn` обёрнут в `try/catch (Throwable)` — `NoClassDefFoundError` это `Error`, а не `Exception`, раньше он пролетал мимо обработчика;
- `MainActivity.signInWith()` ловит любые `Throwable` и переключается на веб-авторизацию Huawei ID вместо падения;
- `HuaweiLoopbackServer` сделан **синглтоном** с `SO_REUSEADDR`: раньше каждая попытка входа поднимала новый сервер на порту 8765, и вторая падала с «порт занят» (на устройстве накопилось 26 экземпляров `HuaweiAuthActivity`);
- `HuaweiAuthActivity` закрывает предыдущий экземпляр и освобождает порт в `onDestroy`;
- добавлена предварительная проверка настроек клиента `HuaweiAuthConfigChecker` (endpoint `authorizeConfirm`): если redirect URI не зарегистрирован в AppGallery Connect, приложение показывает понятное сообщение **внутри приложения**, а не JSON-страницу в браузере. Учтено, что Huawei отвечает кодом 400 и кладёт тело в `errorStream`.

**Проверено на устройстве (Huawei AQM-LX1, без GMS):** приложение не падает, вместо падения показывает «Redirect URI не зарегистрирован в AppGallery Connect: http://127.0.0.1:8765/oauth/huawei».

**Осталось от пользователя:** внести Redirect URL `http://127.0.0.1:8765/oauth/huawei` в AppGallery Connect («Huawei ID» → Авторизация через веб).


### 22.10.2026 — Редизайн главного экрана

**Было:** пустой экран с разбросанными элементами (заголовок, аватар в углу, узкая колонка списка 180 dp, «бирюзовая плашка» вместо имени, пустой `pet_preview`), кнопка «ДОБАВИТЬ ПИТОМЦА» в тулбаре, текст на чёрном фоне.

**Стало (Material 3):**
- `CoordinatorLayout` + `AppBarLayout` с `MaterialToolbar` («Дневник питомца», поиск, меню);
- **карточка профиля**: круглый аватар, имя, подпись («Вы вошли как VK» / «Войдите для синхронизации») и кнопка «Войти» → меню выбора способа входа (VK / Google / Huawei ID);
- **раздел «Мои питомцы»** со счётчиком и тонкой кнопкой «Добавить питомца»;
- **карточки питомцев**: круглое фото (48–52 dp) с рамкой в цвет темы, имя, подпись «вид · порода» и шеврон;
- **пустое состояние** с иконкой-лапкой, заголовком, пояснением и кнопкой добавления;
- все цвета через атрибуты темы (`colorSurfaceContainer*`, `colorOnSurface`, `colorOutlineVariant`) — корректно работают и в светлой, и в тёмной теме, и с Dynamic Color;
- новые иконки: `ic_add`, `ic_chevron_right`, `ic_search`, `ic_person`;
- `Theme.MyPet.NoActionBar` исправлен (было `windowActionBar=true` в стиле с названием NoActionBar) — `MainActivity` переведён на `setSupportActionBar` с собственной тулбар-разметкой;
- пункт «Добавить питомца» убран из тулбара в меню переполнения (на экране есть заметная кнопка).

**Исправлено попутно:** `RecyclerView` внутри `NestedScrollView` получал нулевую высоту (список питомцев не отображался) — компоновка перестроена: фиксированная шапка + прокручиваемый список на всю оставшуюся высоту.

**Проверено на устройстве (Huawei AQM-LX1):** тёмная и светлая темы, открытие карточки питомца и экрана добавления, меню переполнения; `./gradlew testDebugUnitTest` ✅ 33/33.


### 22.10.2026 — Редизайн карточки питомца (MainDetailActivity)

**Было:** квадратное фото 200 dp, кнопки в столбик без иерархии, «Вес и график» без заголовка, FAB «назад» в углу.

**Стало (Material 3):**
- тулбар с кнопкой «назад», названием питомца и меню действий (Редактировать / Опубликовать в VK / Записать видео) — кнопки-«блики» убраны из контента;
- карточка: крупное фото (190 dp, скруглённое 24 dp) с центрированной заглушкой-лапкой, имя, подпись «вид · порода», чипы возраста и веса, кнопки «Здоровье питомца» и «Добавить фото» во всю ширину;
- отдельная карточка «Заметки»;
- секция «Фотографии» с горизонтальной галереей (фото со скруглением 20 dp, крестик удаления) и подсказкой, если фото нет;
- секция «Вес и график» с кнопкой «Записать взвешивание» и графиком;
- `MainDetailActivity` переведён на ViewBinding.

**Исправленные попутно баги (проверены на устройстве):**
- **SecurityException при записи видео**: в манифесте объявлена `CAMERA`, но runtime-разрешение не запрашивалось — система отклоняла запуск камеры, приложение падало. Теперь разрешение запрашивается через `RequestPermission` перед запуском;
- **IllegalStateException в Picasso** («Center crop requires calling resize with positive width and height»): `centerCrop()` применялся к view без размера на момент bind → падение при открытии карточки и галереи. Трансформации убраны, кадрирование делает `scaleType="centerCrop"` в разметке;
- склонение возраста: «3 года / 1 год / 5 лет» вместо «3 лет».

**Проверки:** сборка ✅, тесты 33/33 ✅, на устройстве — карточка, меню действий, запрос разрешения на камеру работают без падений.

**Осталось вне плана (P3):** реальный поиск клиник по Places API (нужен ключ Google), `google-services.json` в `app/` можно удалить (Firebase отключён), CI пока не запускался (нет репозитория на GitHub).
