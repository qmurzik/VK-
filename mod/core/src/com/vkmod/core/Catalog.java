package com.vkmod.core;

import com.vkmod.core.Feature.Category;

import java.util.ArrayList;
import java.util.List;

/** The complete list of features. Numbers follow the product specification. */
final class Catalog {
    private Catalog() {}

    static List<Feature> all() {
        List<Feature> l = new ArrayList<>();
        Category A = Category.APPEARANCE, AD = Category.ADS, M = Category.MESSAGES, MD = Category.MEDIA,
                P = Category.PRIVACY, C = Category.CONVENIENCE, MOD = Category.MOD;

        // ---- Внешний вид ----
        l.add(Feature.of("theme_engine", 1, "Движок тем", A).on()
                .desc("Подмена цветов всего приложения по смыслу токена (акцент, фон, шапка, пузыри…). Без него темы ниже не действуют.").build());
        l.add(Feature.of("theme_amoled", 2, "AMOLED-чёрная тема", A)
                .desc("Чистый чёрный фон, шапка и панели в тёмной теме. Экономит заряд на OLED.").build());
        l.add(Feature.of("material_you", 3, "Material You", A)
                .desc("Цвета из обоев (Android 12+): акцент, фон, карточки, пузыри. На старых версиях ничего не делает.").build());
        String[] presets = new String[com.vkmod.theme.Presets.ALL.size() * 2];
        for (int i = 0; i < com.vkmod.theme.Presets.ALL.size(); i++) {
            presets[i * 2] = com.vkmod.theme.Presets.ALL.get(i).id;
            presets[i * 2 + 1] = com.vkmod.theme.Presets.ALL.get(i).title;
        }
        l.add(Feature.of("theme_preset", 4, "Готовые темы", A).choice("none", presets)
                .desc("AMOLED, Nord, Catppuccin, Dracula, Sepia, Solarized, Gruvbox и другие. Для светлой и тёмной темы VK цвета свои.").build());
        l.add(Feature.of("theme_editor", 5, "Редактор тем", A).action()
                .desc("Свои цвета по каждому слоту с живым предпросмотром, отдельно для светлой и тёмной темы.").build());
        l.add(Feature.of("theme_io", 6, "Импорт и экспорт тем", A).action()
                .desc("Сохранить тему в JSON-файл и загрузить чужую.").build());
        l.add(Feature.of("app_font", 7, "Шрифт приложения", A)
                .choice("system", "system", "Системный", "serif", "С засечками", "mono", "Моноширинный", "casual", "Рукописный", "custom", "Свой TTF")
                .partial("Применяется к тексту, созданному из XML-вёрстки; текст, нарисованный на canvas (часть чата), не меняется.")
                .desc("Заменяет шрифт текстовых полей. Для «Свой TTF» выберите файл кнопкой ниже.").build());
        l.add(Feature.of("app_font_file", 7, "Файл шрифта (TTF)", A).action()
                .desc("Выбрать .ttf для варианта «Свой TTF».").aux().build());
        l.add(Feature.of("text_scale", 8, "Масштаб текста", A).integer(50, 200, 100)
                .partial("Применяется при открытии экрана; уже открытые экраны обновятся после перезахода.")
                .desc("Масштаб шрифтов в процентах независимо от системных настроек (50–200).").build());
        l.add(Feature.of("ui_density", 10, "Компактный режим", A).integer(70, 130, 100)
                .partial("Меняет плотность (dpi) приложения целиком: 70–100 = компактнее, 100–130 = крупнее. Применяется при открытии экрана.")
                .desc("Масштаб всего интерфейса: отступы, иконки, шрифты.").build());
        l.add(Feature.of("bars_alpha", 11, "Прозрачность панелей", A).integer(0, 100, 100)
                .partial("Меняет только непрозрачность цвета шапки и нижней панели; размытие содержимого под панелью недоступно.")
                .desc("Непрозрачность верхней и нижней панелей, %.").build());
        l.add(Feature.of("avatar_shape", 12, "Форма аватарок", A)
                .choice("circle", "circle", "Круг (как в VK)", "squircle", "Скруглённый квадрат", "square", "Квадрат")
                .partial("Экспериментально: форма применяется к аватаркам, созданным из XML; на части экранов остаётся круг.")
                .desc("Форма картинок-аватарок.").build());
        l.add(Feature.of("chat_bg", 13, "Фон чатов", A).action()
                .partial("Только однотонный цвет (слот «Фон чата» в редакторе тем); картинка и градиент недоступны.")
                .desc("Свой цвет фона переписки: откройте редактор тем и выберите слот «Фон чата».").build());
        l.add(Feature.of("bubble_style", 14, "Стиль пузырей сообщений", A).action()
                .partial("Меняются цвета своих и чужих сообщений (слоты в редакторе); форма и скругления пузырей не меняются.")
                .desc("Цвета пузырей переписки: откройте редактор тем, слоты «Исходящие» и «Входящие сообщения».").build());
        l.add(Feature.of("app_icon", 15, "Иконка приложения", A).action()
                .desc("Альтернативные иконки VK. Лаунчер может обновить иконку не сразу.").build());
        l.add(Feature.of("anim_speed", 16, "Скорость анимаций", A).integer(0, 300, 100)
                .partial("Использует скрытое API Android; на части прошивок недоступно. 0 = выключить анимации.")
                .desc("Масштаб длительности анимаций, % (0 — без анимаций).").build());
        l.add(Feature.of("nav_hide_tabs", 17, "Скрыть вкладки нижней панели", A).text("")
                .partial("Скрывает вкладки по имени (news, discover, messages, clips, friends, groups, profile…); порядок вкладок не меняется.")
                .desc("Список вкладок для скрытия через запятую, например: clips, discover.").build());
        l.add(Feature.of("nav_hide_labels", 17, "Скрыть подписи вкладок", A)
                .desc("Только иконки в нижней панели.").build());
        l.add(Feature.of("start_tab", 18, "Стартовая вкладка", A)
                .choice("default", "default", "Как в VK", "tab_news", "Лента", "tab_messages", "Сообщения", "tab_clips", "Клипы", "tab_discover", "Обзор", "tab_friends", "Друзья", "tab_profile", "Профиль")
                .partial("Переключение выполняется после запуска приложения нажатием на вкладку; при запуске из уведомления не применяется.")
                .desc("Какая вкладка открывается при запуске.").build());
        l.add(Feature.of("hide_badges", 19, "Скрыть счётчики и бейджи", A)
                .partial("Скрывает числовые бейджи на вкладках нижней панели.")
                .desc("Убирает красные счётчики непрочитанного на вкладках.").build());

        // ---- Реклама и чистота ----
        l.add(Feature.of("ads_feed", 20, "Реклама в ленте", AD).on()
                .desc("Вырезает рекламные записи (type=ads, marked_as_ads, ad_data) из ответов сервера и блокирует рекламные хосты.").build());
        l.add(Feature.of("promo_communities", 21, "Промо и «рекомендуемые сообщества»", AD).on()
                .desc("Убирает блоки рекомендуемых сообществ, каналов, чатов и промо-беседы в списке диалогов.").build());
        l.add(Feature.of("ads_stories", 22, "Реклама в историях", AD).on()
                .desc("Удаляет рекламные настройки из ответов историй.").build());
        l.add(Feature.of("ads_clips", 23, "Реклама в клипах и видео", AD).on()
                .partial("Вырезает рекламные дескрипторы видео (ads_info); форма рекламы в клипах проверена только по описанию API.")
                .desc("Убирает рекламные вставки перед и между видео.").build());
        l.add(Feature.of("banners", 24, "Баннеры и всплывающие предложения", AD).on()
                .desc("Информационные блоки, тизеры подписок (Donut), опросы обратной связи и сплэш-баннеры.").build());
        l.add(Feature.of("feed_reco", 25, "Рекомендации в ленте", AD)
                .desc("Записи из сообществ, на которые вы не подписаны, и блоки «для вас» (музыка, видео, игры, товары).").build());
        l.add(Feature.of("friends_suggest", 26, "«Возможные друзья»", AD)
                .desc("Убирает предложения дружбы и точки входа друзей из ленты.").build());
        l.add(Feature.of("hide_stories", 27, "Скрыть блок историй", AD)
                .desc("Лента историй сверху не загружается.").build());
        l.add(Feature.of("hide_view_ids", 28, "Скрыть лишние элементы интерфейса", AD).text("")
                .partial("Работает для вёрстки из XML; нужно знать имя элемента (id). Примеры: ad_label, posting_header.")
                .desc("Список имён id через запятую; такие элементы скрываются везде.").build());
        l.add(Feature.of("keywords", 29, "Фильтр ленты по словам", AD).text("")
                .desc("Записи с этими словами скрываются (через запятую, без учёта регистра).").build());
        l.add(Feature.of("hide_reposts", 30, "Скрыть репосты", AD)
                .desc("Записи-репосты не показываются в ленте.").build());
        l.add(Feature.of("custom_blocks", 31, "Свои блокировки запросов", AD).text("")
                .desc("Дополнительные хосты или методы API для блокировки, через пробел или запятую (например: stats.trackEvents, ads.example.com).").build());

        l.add(Feature.of("feed_clips", 0, "Клипы в ленте", AD)
                .desc("Убирает блоки и карточки клипов из ленты новостей.").build());
        l.add(Feature.of("feed_games", 0, "Игры и мини-приложения в ленте", AD)
                .desc("Карусели игр, достижения, виджеты и рекомендации мини-приложений.").build());
        l.add(Feature.of("feed_market", 0, "Товары и магазины в ленте", AD)
                .desc("Карусели маркета и Юлы, рекомендации магазинов.").build());
        l.add(Feature.of("feed_dzen", 0, "Дзен в ленте", AD)
                .desc("Блоки и истории Дзена.").build());
        l.add(Feature.of("feed_music", 0, "Музыкальные подборки в ленте", AD)
                .desc("Плейлисты, исполнители и «музыка для вас».").build());
        l.add(Feature.of("feed_video", 0, "Видеоблоки в ленте", AD)
                .desc("«Видео для вас», видеооткрытки и подборки медиа.").build());
        l.add(Feature.of("feed_misc", 0, "Дайджесты, эксперты, интересы", AD)
                .desc("Дайджесты, карточки экспертов, трансляции TextLive, блок интересов.").build());

        l.add(Feature.of("feed_friend_events", 0, "«Стали друзьями» в ленте", AD)
                .desc("Убирает записи о новых друзьях ваших друзей.").build());
        l.add(Feature.of("feed_photo_tags", 0, "Отметки на фото в ленте", AD)
                .desc("Убирает записи «отметил(а) на фото».").build());
        l.add(Feature.of("feed_only_friends", 0, "Только люди в ленте", AD)
                .desc("Скрывает записи сообществ: остаются записи друзей и людей, на которых вы подписаны.").build());
        l.add(Feature.of("feed_only_groups", 0, "Только сообщества в ленте", AD)
                .desc("Скрывает личные записи людей: остаются записи сообществ.").build());
        l.add(Feature.of("feed_poll_posts", 0, "Скрыть записи с опросами", AD)
                .desc("Записи с вложенным опросом не показываются в ленте.").build());
        l.add(Feature.of("custom_feed_types", 0, "Свои типы записей ленты", AD).text("")
                .desc("Дополнительные значения type через запятую, которые нужно убирать из ленты (для опытных).").build());
        l.add(Feature.of("hide_ad_labels", 0, "Скрыть пометки «Реклама»", AD).on()
                .desc("Убирает плашки и дисклеймеры рекламы, которые остались в вёрстке.").build());

        // ---- Сообщения ----
        l.add(Feature.of("no_read", 32, "Нечиталка", M)
                .desc("Не отправлять «прочитано»: собеседник не увидит, что вы открыли чат.").build());
        l.add(Feature.of("no_typing", 33, "Не показывать «печатает»", M)
                .desc("Не отправлять статус набора текста и записи голосового.").build());
        l.add(Feature.of("select_text", 38, "Выделение и копирование текста", M)
                .partial("Включает выделение у текстовых полей из XML-вёрстки; пузыри сообщений, нарисованные вручную, могут не поддерживать.")
                .desc("Делает текст на экранах выделяемым.").build());
        l.add(Feature.of("no_voice_listened", 0, "Не отмечать голосовые прослушанными", M)
                .desc("Отправитель не увидит, что вы прослушали его голосовое сообщение.").build());
        l.add(Feature.of("dl_voice", 39, "Скачивание голосовых сообщений", M)
                .desc("Голосовые из переписки попадают в «Журнал медиа» (настройки мода) со ссылкой на скачивание.").build());

        // ---- Медиа ----
        l.add(Feature.of("dl_photo", 40, "Скачивание фото в оригинале", MD)
                .desc("Фото из ответов сервера собираются в «Журнале медиа» с максимальным разрешением.").build());
        l.add(Feature.of("dl_video", 41, "Скачивание видео", MD)
                .desc("Ссылки на mp4 лучшего качества собираются в «Журнале медиа».").build());
        l.add(Feature.of("dl_clips", 42, "Скачивание клипов", MD)
                .desc("Клипы собираются в «Журнале медиа».").build());
        l.add(Feature.of("dl_stories", 43, "Скачивание историй", MD)
                .desc("Фото и видео историй собираются в «Журнале медиа».").build());
        l.add(Feature.of("media_log", 40, "Журнал медиа", MD).action()
                .desc("Открыть список собранных ссылок и скачать файлы в «Загрузки».").aux().build());

        // ---- Приватность ----
        l.add(Feature.of("offline", 46, "Офлайн-режим", P)
                .desc("Не отправлять статус «в сети»: вас не видно онлайн.").build());
        l.add(Feature.of("block_trackers", 48, "Отключение трекеров и аналитики", P).on()
                .desc("Блокирует аналитику (myTracker, AppMetrica, Crashlytics…) и методы stats.*, adsint.*.").build());
        l.add(Feature.of("no_listen_broadcast", 0, "Не показывать, что вы слушаете музыку", P)
                .desc("Не транслировать трек в статус профиля.").build());
        l.add(Feature.of("app_lock", 49, "Блокировка приложения", P)
                .desc("PIN-код или отпечаток при входе в приложение.").build());
        l.add(Feature.of("app_lock_pin", 49, "PIN-код", P).action()
                .desc("Задать или сменить PIN-код.").aux().build());
        l.add(Feature.of("app_lock_bio", 49, "Вход по биометрии", P).on()
                .desc("Разрешить отпечаток/лицо вместо PIN-кода.").aux().build());
        l.add(Feature.of("app_lock_grace", 49, "Не блокировать в течение", P).integer(0, 600, 30)
                .desc("Секунд после выхода из приложения, пока блокировка не требуется.").aux().build());
        l.add(Feature.of("proxy", 50, "Прокси", P).text("")
                .partial("Адрес вида socks5://host:port или http://host:port. Применяется к соединениям, созданным после запуска; перезапустите VK.")
                .desc("Пустое поле — без прокси.").build());
        l.add(Feature.of("allow_screenshots", 0, "Разрешить скриншоты везде", P)
                .desc("Снимает флаг защиты экрана (FLAG_SECURE) там, где VK его ставит.").build());

        // ---- Удобство ----
        l.add(Feature.of("no_easter_eggs", 0, "Отключить праздничные спецэффекты", C)
                .desc("Не загружать сезонные эффекты и пасхалки.").build());
        l.add(Feature.of("exact_dates", 51, "Точная дата и время", C)
                .partial("Работает там, где VK вызывает свой общий форматтер времени; на части экранов (например, в чатах) даты остаются прежними.")
                .desc("Вместо «вчера» и «2 часа назад» показывать дату и время, например 5 марта 2025, 21:40.").build());
        l.add(Feature.of("no_haptics", 55, "Отключить вибрацию и звуки интерфейса", C)
                .desc("Отключает тактильный отклик и звуки нажатий у элементов интерфейса.").build());
        l.add(Feature.of("keep_screen_on", 0, "Не гасить экран в приложении", C)
                .desc("Экран не гаснет, пока открыто приложение.").build());

        // ---- Мод ----
        l.add(Feature.of("backup", 56, "Резервная копия настроек", MOD).action()
                .desc("Сохранить и восстановить все настройки мода одним файлом.").build());
        l.add(Feature.of("mod_log", 57, "Лог работы мода", MOD).action()
                .desc("Диагностика: какие хуки сработали и какие функции отключились из-за ошибок.").build());
        l.add(Feature.of("update_check", 58, "Проверка обновлений мода", MOD).action()
                .desc("Сверяется с последним релизом на GitHub.").build());
        l.add(Feature.of("reset_all", 59, "Сбросить все настройки", MOD).action()
                .desc("Возвращает настройки мода к значениям по умолчанию.").build());
        return l;
    }
}
