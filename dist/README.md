# QVK 8.100-qvk-3.0.0-alpha25

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha25.apk.part0 QVK-alpha25.apk.part1 QVK-alpha25.apk.part2 > QVK-8.100-qvk-3.0.0-alpha25.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha25.apk
# 7b21dee8310066d9c87d5638d1a54b9c050c9d02e7359fd8a62a785a9f7542ce
```

Windows (cmd): `copy /b QVK-alpha25.apk.part0+QVK-alpha25.apk.part1+QVK-alpha25.apk.part2 QVK-8.100-qvk-3.0.0-alpha25.apk`

Пакет `ru.qmods.qvk`, versionCode 23824, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
