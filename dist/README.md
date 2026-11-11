# QVK 8.100-qvk-3.0.0-alpha13

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha13.apk.part0 QVK-alpha13.apk.part1 QVK-alpha13.apk.part2 > QVK-8.100-qvk-3.0.0-alpha13.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha13.apk
# 030260bd57dc60601d240443306e515c1b1485d2b72024f757a0dc12fc39eff4
```

Windows (cmd): `copy /b QVK-alpha13.apk.part0+QVK-alpha13.apk.part1+QVK-alpha13.apk.part2 QVK-8.100-qvk-3.0.0-alpha13.apk`

Пакет `ru.qmods.qvk`, versionCode 23812, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
