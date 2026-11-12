# QVK 8.100-qvk-3.0.0-alpha37

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha37.apk.part0 QVK-alpha37.apk.part1 > QVK-8.100-qvk-3.0.0-alpha37.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha37.apk
# 66af11f7edf9f57e30c1ac1ed3e8f5d653ca41d34f4bb9146ac3e105c86c7676
```

Windows (cmd): `copy /b QVK-alpha37.apk.part0+QVK-alpha37.apk.part1 QVK-8.100-qvk-3.0.0-alpha37.apk`

Пакет `ru.qmods.qvk`, versionCode 23836, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
