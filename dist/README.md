# QVK 8.100-qvk-3.0.0-alpha45

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha45.apk.part0 QVK-alpha45.apk.part1 > QVK-8.100-qvk-3.0.0-alpha45.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha45.apk
# 7dc9a2f4aaa6705622d3675af2c8c105757561d0ebaedf173f04038e80b5ff76
```

Windows (cmd): `copy /b QVK-alpha45.apk.part0+QVK-alpha45.apk.part1 QVK-8.100-qvk-3.0.0-alpha45.apk`

Пакет `ru.qmods.qvk`, versionCode 23844, ставится поверх QVK 2.6.18 (подпись та же).
