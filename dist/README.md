# QVK 8.100-qvk-3.0.0-alpha16

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha16.apk.part0 QVK-alpha16.apk.part1 QVK-alpha16.apk.part2 > QVK-8.100-qvk-3.0.0-alpha16.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha16.apk
# abb4a5114cdce51c701678cc98c631cbf7fb6f974ca689408582062d8ef95f08
```

Windows (cmd): `copy /b QVK-alpha16.apk.part0+QVK-alpha16.apk.part1+QVK-alpha16.apk.part2 QVK-8.100-qvk-3.0.0-alpha16.apk`

Пакет `ru.qmods.qvk`, versionCode 23815, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
