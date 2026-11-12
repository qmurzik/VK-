# QVK 8.100-qvk-3.0.0-alpha39

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha39.apk.part0 QVK-alpha39.apk.part1 > QVK-8.100-qvk-3.0.0-alpha39.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha39.apk
# 8007de4cae1cfbfdc37885ee0fa997b7d0b483095507c671c88525359f855254
```

Windows (cmd): `copy /b QVK-alpha39.apk.part0+QVK-alpha39.apk.part1 QVK-8.100-qvk-3.0.0-alpha39.apk`

Пакет `ru.qmods.qvk`, versionCode 23838, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
