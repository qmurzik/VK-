# QVK 8.100-qvk-3.0.0-alpha38

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha38.apk.part0 QVK-alpha38.apk.part1 > QVK-8.100-qvk-3.0.0-alpha38.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha38.apk
# 1a56d7b0b85d8837057d5167ec1eccec7c10e83a64d4ffa47607240848f2a265
```

Windows (cmd): `copy /b QVK-alpha38.apk.part0+QVK-alpha38.apk.part1 QVK-8.100-qvk-3.0.0-alpha38.apk`

Пакет `ru.qmods.qvk`, versionCode 23837, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
