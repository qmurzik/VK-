# QVK 8.100-qvk-3.0.0-alpha34

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha34.apk.part0 QVK-alpha34.apk.part1 QVK-alpha34.apk.part2 > QVK-8.100-qvk-3.0.0-alpha34.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha34.apk
# 006ff09ab15be8cc574e4b69141398e4cdf52bcfa146e2697071c4e49da1d348
```

Windows (cmd): `copy /b QVK-alpha34.apk.part0+QVK-alpha34.apk.part1+QVK-alpha34.apk.part2 QVK-8.100-qvk-3.0.0-alpha34.apk`

Пакет `ru.qmods.qvk`, versionCode 23833, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
