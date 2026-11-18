# QVK 8.100-qvk-3.1.0 — внезапное обновление

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-3.1.0.apk.part0 QVK-3.1.0.apk.part1 > QVK-8.100-qvk-3.1.0.apk
sha256sum QVK-8.100-qvk-3.1.0.apk
# cd95b500daa3ed0daa3a5f0805c82bf5de46857513e118a7fb16827b0cda1dfb
```

Windows (cmd): `copy /b QVK-3.1.0.apk.part0+QVK-3.1.0.apk.part1 QVK-8.100-qvk-3.1.0.apk`

Пакет `ru.qmods.qvk`, versionCode 23847, ставится поверх QVK 2.6.18 и всех сборок 3.0 (подпись та же).
