# QVK 8.100-qvk-3.0.0-alpha42

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha42.apk.part0 QVK-alpha42.apk.part1 > QVK-8.100-qvk-3.0.0-alpha42.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha42.apk
# 7a9bb35988bfd0ffb809be8588fb3098ded8daee91bf7ee79d1bbe96a10df914
```

Windows (cmd): `copy /b QVK-alpha42.apk.part0+QVK-alpha42.apk.part1 QVK-8.100-qvk-3.0.0-alpha42.apk`

Пакет `ru.qmods.qvk`, versionCode 23841, ставится поверх QVK 2.6.18 (подпись та же).
