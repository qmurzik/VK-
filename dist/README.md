# QVK 8.100-qvk-3.0.0-alpha43

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha43.apk.part0 QVK-alpha43.apk.part1 > QVK-8.100-qvk-3.0.0-alpha43.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha43.apk
# ee2fd359e0b7a3ee81c03e6a8d1df11e574cff3344163f528ee3e8c7e8a51d0d
```

Windows (cmd): `copy /b QVK-alpha43.apk.part0+QVK-alpha43.apk.part1 QVK-8.100-qvk-3.0.0-alpha43.apk`

Пакет `ru.qmods.qvk`, versionCode 23842, ставится поверх QVK 2.6.18 (подпись та же).
