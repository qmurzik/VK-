# QVK 8.100-qvk-3.0.0-alpha36

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha36.apk.part0 QVK-alpha36.apk.part1 QVK-alpha36.apk.part2 > QVK-8.100-qvk-3.0.0-alpha36.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha36.apk
# 2afcba9da522fa36f52e189b70734e8d561df1d97a73c2c2361a5eea9dfd0b91
```

Windows (cmd): `copy /b QVK-alpha36.apk.part0+QVK-alpha36.apk.part1+QVK-alpha36.apk.part2 QVK-8.100-qvk-3.0.0-alpha36.apk`

Пакет `ru.qmods.qvk`, versionCode 23835, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
