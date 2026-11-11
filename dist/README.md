# QVK 8.100-qvk-3.0.0-alpha22

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha22.apk.part0 QVK-alpha22.apk.part1 QVK-alpha22.apk.part2 > QVK-8.100-qvk-3.0.0-alpha22.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha22.apk
# c50fe595be5b804c4251f8b0dea4e66eda6be3b1cde5c840ad3252590ffa6e16
```

Windows (cmd): `copy /b QVK-alpha22.apk.part0+QVK-alpha22.apk.part1+QVK-alpha22.apk.part2 QVK-8.100-qvk-3.0.0-alpha22.apk`

Пакет `ru.qmods.qvk`, versionCode 23821, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
