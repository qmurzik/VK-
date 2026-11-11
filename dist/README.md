# QVK 8.100-qvk-3.0.0-alpha21

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha21.apk.part0 QVK-alpha21.apk.part1 QVK-alpha21.apk.part2 > QVK-8.100-qvk-3.0.0-alpha21.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha21.apk
# be992625040c774b62599579345c3eb06e0a18ed2af5e7d4ef25b04b50c31ce2
```

Windows (cmd): `copy /b QVK-alpha21.apk.part0+QVK-alpha21.apk.part1+QVK-alpha21.apk.part2 QVK-8.100-qvk-3.0.0-alpha21.apk`

Пакет `ru.qmods.qvk`, versionCode 23820, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
