# QVK 8.100-qvk-3.0.0-alpha18

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha18.apk.part0 QVK-alpha18.apk.part1 QVK-alpha18.apk.part2 > QVK-8.100-qvk-3.0.0-alpha18.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha18.apk
# 84700cade30494e0c06b2400067171dd8b45ccca2dbc5235df8faaa5a943adb7
```

Windows (cmd): `copy /b QVK-alpha18.apk.part0+QVK-alpha18.apk.part1+QVK-alpha18.apk.part2 QVK-8.100-qvk-3.0.0-alpha18.apk`

Пакет `ru.qmods.qvk`, versionCode 23817, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
