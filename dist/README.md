# QVK 8.100-qvk-3.0.0-alpha20

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha20.apk.part0 QVK-alpha20.apk.part1 QVK-alpha20.apk.part2 > QVK-8.100-qvk-3.0.0-alpha20.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha20.apk
# 82f19bd1a9c39fb83abeca44134a1f2af3b11478238aee9e8b66129e71a50eaf
```

Windows (cmd): `copy /b QVK-alpha20.apk.part0+QVK-alpha20.apk.part1+QVK-alpha20.apk.part2 QVK-8.100-qvk-3.0.0-alpha20.apk`

Пакет `ru.qmods.qvk`, versionCode 23819, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
