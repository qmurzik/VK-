# QVK 8.100-qvk-3.0.0-alpha32

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha32.apk.part0 QVK-alpha32.apk.part1 QVK-alpha32.apk.part2 > QVK-8.100-qvk-3.0.0-alpha32.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha32.apk
# 7a84130e70e41bb7aedcc811b5c98a632751c7a5451a43f25ec49d76c2f20ffd
```

Windows (cmd): `copy /b QVK-alpha32.apk.part0+QVK-alpha32.apk.part1+QVK-alpha32.apk.part2 QVK-8.100-qvk-3.0.0-alpha32.apk`

Пакет `ru.qmods.qvk`, versionCode 23831, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
