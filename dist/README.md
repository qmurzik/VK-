# QVK 8.100-qvk-3.0.0-alpha15

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha15.apk.part0 QVK-alpha15.apk.part1 QVK-alpha15.apk.part2 > QVK-8.100-qvk-3.0.0-alpha15.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha15.apk
# 4999a84fd7025138a54b2add4834aba9b22e6be30e0b353a52011892ef3bfc94
```

Windows (cmd): `copy /b QVK-alpha15.apk.part0+QVK-alpha15.apk.part1+QVK-alpha15.apk.part2 QVK-8.100-qvk-3.0.0-alpha15.apk`

Пакет `ru.qmods.qvk`, versionCode 23814, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
