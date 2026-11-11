# QVK 8.100-qvk-3.0.0-alpha17

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha17.apk.part0 QVK-alpha17.apk.part1 QVK-alpha17.apk.part2 > QVK-8.100-qvk-3.0.0-alpha17.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha17.apk
# c1a7d33684854579db987a8623a160adf444f668550353691958f9d163965fe9
```

Windows (cmd): `copy /b QVK-alpha17.apk.part0+QVK-alpha17.apk.part1+QVK-alpha17.apk.part2 QVK-8.100-qvk-3.0.0-alpha17.apk`

Пакет `ru.qmods.qvk`, versionCode 23816, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
