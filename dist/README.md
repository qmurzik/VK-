# QVK 8.100-qvk-3.0.0-alpha29

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha29.apk.part0 QVK-alpha29.apk.part1 QVK-alpha29.apk.part2 > QVK-8.100-qvk-3.0.0-alpha29.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha29.apk
# e77d2eee0e65e660e499350753fbb2d7728311725212e6fb8fbe802a62d779c1
```

Windows (cmd): `copy /b QVK-alpha29.apk.part0+QVK-alpha29.apk.part1+QVK-alpha29.apk.part2 QVK-8.100-qvk-3.0.0-alpha29.apk`

Пакет `ru.qmods.qvk`, versionCode 23828, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
