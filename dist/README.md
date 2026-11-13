# QVK 8.100-qvk-3.0.0-alpha46

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha46.apk.part0 QVK-alpha46.apk.part1 > QVK-8.100-qvk-3.0.0-alpha46.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha46.apk
# a4b17491a61fac6280d9a78404c4344ac61fe713aa1dc29b6e475ae6a6e4b247
```

Windows (cmd): `copy /b QVK-alpha46.apk.part0+QVK-alpha46.apk.part1 QVK-8.100-qvk-3.0.0-alpha46.apk`

Пакет `ru.qmods.qvk`, versionCode 23845, ставится поверх QVK 2.6.18 (подпись та же).
