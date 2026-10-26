# Карта точек вмешательства — VK 8.200

Источник: `com.vkontakte.android` 8.200, versionCode 59646 (сборка AppGallery, arm64-v8a + armeabi-v7a, один APK, 20 dex).
SHA-256: `7ea8fab1a56060fdb69183dda26d75d00c6dccaef8f83fc2448626d20aefa35d`.
Подпись оригинала: O=VKontakte, SHA-256 сертификата `057d9744…f1ef32`.

Статус: **черновик**. «✔ найдено» — класс или строка подтверждены в декомпиляции; «? проверить» — признак поиска
определён, но точка в коде ещё не открыта. Имена `xsna/*` обфусцированы и меняются между версиями, поэтому
патч-скрипт ищет их по признаку, а не по имени.

## Общие факты

| Что | Где | Признак поиска | Статус |
|---|---|---|---|
| Application | `com.vkontakte.android.VKApplication` (манифест) | `<application android:name>` | ✔ |
| Сеть | OkHttp, пакет `okhttp3` не зашейден: `okhttp3.Interceptor` (метод `intercept(Interceptor.a)`), `Request`=`okhttp3.p`, `Response`=`okhttp3.u` | интерфейс `okhttp3/Interceptor` | ✔ |
| Тема | `xsna.xnz0` (VKThemeHelper): `f` = резолвер цветов `tnz0`, `H()` текущая тема, `h(VKActivity)` ставит стиль, `e/d` = пары `ynz0(light, dark)` | `.source "VKThemeHelper.kt"` | ✔ |
| Фасад темы | `xsna.ae41` (VkThemeHelperBase): `o(attr, ctx)` разрешает цвет, `p/q/r/s` красят view | `.source "VkThemeHelperBase.kt"` | ✔ |
| Стили тем | `Vk.Theme.VkApp.*_generated`, `Lego.Light/Dark`: ≈460 атрибутов `vk_ui_*` (463) и `vk_legacy_*`; всего 547 цветовых атрибутов `vk_*` | `res/values/styles.xml` | ✔ |
| Ресурсы | `res/values/attrs.xml`, `colors.xml`, `res/color-night`, `values-night` | — | ✔ |
| Нижняя панель | `com.vkontakte.android.ui.bottomnavigation.BottomNavigationItemView`, `xsna.xbw0`, `TabMenuItemUiData` (`tab_news`, `tab_messages`) | `tabbar_active_icon`, `tab_news` | ✔ |
| Иконки приложения | 10+ `activity-alias` `com.vk.android.launcher.icons.IconAlias.*` в манифесте | `activity-alias` | ✔ |
| Проверка подписи | `xsna.uon` (хеш подписи `SigningInfo`), `xsna.n37` (`InvalidSignatureException`, VK Push SDK) | `SigningInfo`, `getApkContentsSigners` | ✔ найдено, назначение ? проверить |
| Аналитика | `xsna.kmo` (строка `stats.trackEvents`) | `stats.trackEvents` | ✔ |
| Реклама (типы) | `NewsfeedNewsfeedItemTypeDto`, `xsna.agy0` (`ads_post`), `WallWallItemDto` (`marked_as_ads`) | `ads_post`, `marked_as_ads` | ✔ |
| Ads SDK | myTarget (`com.my.target`), Unity Ads (`com.unity3d.ads`) | пакеты | ✔ |
| Push | `com.vk.push`, `com.vk.pushes`, `fcmerrorhandler`; VK Push SDK проверяет подпись | пакеты | ✔, поведение ? проверить |

## Функции → точка → признак

Колонка «Способ»: **net** = перехват OkHttp (версионно-независимо), **theme** = хук темы, **smali** = точечный
патч, **res** = правка ресурсов, **core** = логика ядра без хука в VK.

| № | Функция | Способ | Точка / признак поиска | Статус |
|---|---|---|---|---|
| 1–4 | Движок тем, AMOLED, пресеты, Material You | theme | хук резолвера `xnz0.f` (`tnz0.a(attr, ctx)`) и `ae41.o`: подмена цвета по имени атрибута (`Resources.getResourceEntryName`) через `ColorSlot`; AMOLED = палитра для тёмной темы | ✔ точка найдена |
| 5–6 | Редактор тем, импорт/экспорт JSON | core | экран настроек мода; сериализация палитры | core |
| 7–8 | Шрифт, масштаб текста | smali/core | `Resources.getConfiguration` (`fontScale`), `Typeface` в `VKApplication.attachBaseContext` | ? проверить |
| 9 | Радиус скруглений | res/theme | атрибуты/dimen `corner*`, `vk_ui_*_radius` в `styles.xml` | ? проверить |
| 10 | Компактный режим | res | dimen и стили списков | ? проверить |
| 11 | Прозрачность/размытие панелей | theme | `header_background_before_blur`, `tabbar_background` | ? проверить |
| 12 | Форма аватарок | smali | view аватара (`VKImageView`/`vk_ui_avatar`) | ? проверить |
| 13–14 | Фон чатов, пузыри | theme | `im_bubble_*`, `im_bg_chat` (в теме `Vk.Theme.VkApp`) | ✔ атрибуты найдены |
| 15 | Иконки приложения | core | `activity-alias` + `PackageManager.setComponentEnabledSetting` | ✔ |
| 16 | Скорость анимаций | core | `Settings.Global` аниматор-scale недоступен; хук `ValueAnimator.setDuration` | core |
| 17–19 | Вкладки, стартовая вкладка, бейджи | smali | `BottomNavigationItemView`, `TabMenuItemUiData` | ? проверить |
| 20–26, 30 | Реклама, промо, рекомендации, репосты | net | фильтр ответов API по `type`/`marked_as_ads`/`copy_history`; логика готова в `ApiFilter.kt` | ✔ логика есть |
| 27–29 | Блок историй, пункты меню, фильтр слов | net/smali | `stories.get` в ответах; меню — `res/menu` и `TabMenuItemUiData` | ? проверить |
| 31 | «Обновите приложение» | net/smali | строки/классы in-app update | ? проверить |
| 32–33 | Нечиталка, «печатает» | net | блокировка запросов `messages.markAsRead`, `messages.setActivity` по URL; **строки метода в коде собираются, прямого литерала нет — нужен URL-фильтр** | ✔ способ подтверждён |
| 36–37, 38 | Подтверждение голосовых, точное время, копирование | smali | экран чата `com.vk.im` | ? проверить |
| 39–45 | Скачивание медиа, автовоспроизведение, качество | smali | `com.vk.media`, `com.vk.clips`, `com.vk.stories`; URL берутся из DTO | ? проверить |
| 46 | Офлайн | net | блокировка `account.setOnline` | ✔ способ |
| 48 | Трекеры/аналитика | net | хосты (`NetRules.TRACKER_HOSTS`), методы `stats.*` | ✔ логика есть |
| 49 | PIN/биометрия | core | `Application.ActivityLifecycleCallbacks` + `BiometricPrompt` | core |
| 50 | Прокси | net | `ProxySelector.setDefault`; OkHttp держит свой `ebl` (ProxySelector) | ? проверить |
| 51–54 | Точные даты, ID, ссылки, двойной тап | smali | форматтеры времени, профиль, `com.vk.double_tap` | ? проверить |
| 55 | Вибрация/звуки | smali | `Vibrator`, `View.performHapticFeedback` | core |
| 56–59 | Бэкап, лог, обновления, сброс | core | хранилище настроек мода | core |

## Исключено осознанно

- №34, 35 (сохранение удалённых и история правок чужих сообщений) и №47 (анонимный просмотр историй): затрагивают
  других людей без их ведома. Замена из резерва: №51–59 уже в плане, дополнительно — «закреплённые чаты локально»,
  «скрытие реакций», «быстрые ответы».
- Обход проверки подписи и защит VK ID / VK Push: не делается.

## Риски переподписи (по найденному)

- `xsna.uon` считает хеш подписи приложения → вероятно, отправляется на сервер/в SDK. Если сервер сверяет подпись,
  часть функций (вход через VK ID, push) может не работать после переподписи. Окончательный ответ — только
  проверкой на устройстве; будет записан в README как ограничение.
- VK Push SDK (`n37`) проверяет подпись вызывающего пакета (`InvalidSignatureException`) — для RuStore-push.
- Поверх оригинала не ставится (другая подпись) — нужно удалить оригинал. Обходной путь для данных: бэкап VK.

## Что делаю дальше

1. Подтвердить пометки «? проверить» (по группам: сообщения, медиа, лента/меню, навигация).
2. Каркас: `core/` (Kotlin → отдельный dex), реестр функций, хранилище, лог; `patcher/` (скрипт, ищущий точки по
   признакам и сообщающий о ненайденных).
3. Первая сборка: тема + сеть (функции 1–4, 13–15, 20–33, 46, 48), затем остальные пачки.
