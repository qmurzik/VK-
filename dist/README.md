# QVK 8.100-qvk-3.0.0-alpha26

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha26.apk.part0 QVK-alpha26.apk.part1 QVK-alpha26.apk.part2 > QVK-8.100-qvk-3.0.0-alpha26.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha26.apk
# dca9c52e25b2be94ad34d9ee8c96cf5d12c44afa03c70afe4ccc490ac7e43ecf
```

Windows (cmd): `copy /b QVK-alpha26.apk.part0+QVK-alpha26.apk.part1+QVK-alpha26.apk.part2 QVK-8.100-qvk-3.0.0-alpha26.apk`

Пакет `ru.qmods.qvk`, versionCode 23825, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
