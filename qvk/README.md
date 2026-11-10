# QVK 3.0 — сборка поверх QVK 2.6.18 (VK 8.100)

Этот каталог содержит **исходники изменений QVK 3.0** и воспроизводимый процесс сборки. Сам APK
VK/QVK в репозиторий не кладётся: скрипт скачивает базовую сборку QVK 2.6.18 и проверяет её SHA-256.

```
qvk/
  base.json              базовый APK (адреса, SHA-256, пакет, versionCode, сертификат подписи)
  release.json           versionCode / versionName собираемого обновления
  build.py               весь конвейер сборки
  src/main/java/         новый код QVK 3.0 (пакет re.sova.qmods, классы Qvk3*)
  src/test/              JVM-тесты чистой логики (pure.txt — какие классы в них участвуют)
  patches/*.patch        точечные smali-патчи к QVK 2.6.18 (unified diff)
  manifest/additions.xml новые компоненты манифеста
  assets/                файлы, добавляемые в assets APK
  tools/LinkCheck.java   статическая проверка ссылок/доступа/дубликатов классов в итоговом APK
  tools/update_manifest.py  генерация ключа и подписанного манифеста обновлений
```

## Сборка

Нужны JDK 17+, Python 3.9+, `patch`, `cp`, интернет (Android SDK, apktool, dex2jar скачиваются сами).

```bash
python3 qvk/build.py          # тесты → APK в qvk/out/QVK-<version>.apk
python3 qvk/build.py test     # только юнит-тесты
```

Переменные: `QVK_CACHE` (кэш, по умолчанию `qvk/.cache`, ~6 ГБ), `ANDROID_HOME` (готовый SDK),
`QVK_BASE_APK` (локальный путь к базовому APK вместо скачивания; SHA-256 всё равно проверяется).

Шаги: скачивание и проверка базы → `apktool d` → classpath (dex2jar) → `javac` против реальных классов
VK/QVK + `d8` → патчи + манифест + версия → `apktool b` + новый dex → `zipalign` → подпись → проверки:
`apksigner verify`, совпадение сертификата с базой (иначе обновление не встанет поверх), пакет и версия,
**LinkCheck** (существование классов/методов/полей, доступ к private/package-private, дубликаты классов).

## Подпись

QVK 2.6.18 подписан **публичным тестовым ключом AOSP** (`testkey`, SHA-256 сертификата
`a40da80a…71bf5dc`). Чтобы обновление устанавливалось поверх без потери данных, QVK 3.0 подписывается
тем же ключом. Важно: этот ключ публичный, поэтому подлинность обновлений нельзя гарантировать подписью
APK — для этого в QVK 3.0 есть подписанный манифест обновлений (см. ниже). Переход на собственный ключ
возможен только с переустановкой (Android запрещает смену подписи без ротации ключа v3, а ротация
должна быть подписана старым ключом — технически возможна через `apksigner rotate`, если решите).

## Публикация обновления

```bash
python3 qvk/tools/update_manifest.py keygen ~/qvk-keys          # один раз; private.pem хранить в секрете
# public key → qvk/assets/qvk3_update_keys.json (keys[].public_key) и пересобрать
python3 qvk/tools/update_manifest.py make qvk/out/QVK-….apk https://зеркало1/QVK.apk https://зеркало2/QVK.apk \
    --key ~/qvk-keys/private.pem --notes "Что нового" > stable.json
```

Загрузите APK на зеркала и `stable.json` на любой HTTPS-хостинг, добавьте адрес в «Настройки QVK →
Обновления QVK → Серверы обновлений» (или поставляйте адрес по умолчанию в следующей сборке).
