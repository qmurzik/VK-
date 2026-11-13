# QVK 8.100-qvk-3.0.0-alpha44

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha44.apk.part0 QVK-alpha44.apk.part1 > QVK-8.100-qvk-3.0.0-alpha44.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha44.apk
# 8df775de1e56b873e25ae289bd5fc7d564ef199766f13313ac7376dbd7d82399
```

Windows (cmd): `copy /b QVK-alpha44.apk.part0+QVK-alpha44.apk.part1 QVK-8.100-qvk-3.0.0-alpha44.apk`

Пакет `ru.qmods.qvk`, versionCode 23843, ставится поверх QVK 2.6.18 (подпись та же).
