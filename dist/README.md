# QVK 8.100-qvk-3.0.0-alpha41

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha41.apk.part0 QVK-alpha41.apk.part1 > QVK-8.100-qvk-3.0.0-alpha41.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha41.apk
# 33ef279f940c24ad92a92a2cd8dde6f9bae6057cb9f1e7ab2ab8d004a99efac4
```

Windows (cmd): `copy /b QVK-alpha41.apk.part0+QVK-alpha41.apk.part1 QVK-8.100-qvk-3.0.0-alpha41.apk`

Пакет `ru.qmods.qvk`, versionCode 23840, ставится поверх QVK 2.6.18 (подпись та же).
