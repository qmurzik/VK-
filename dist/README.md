# QVK 8.100-qvk-3.0.0-alpha33

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha33.apk.part0 QVK-alpha33.apk.part1 QVK-alpha33.apk.part2 > QVK-8.100-qvk-3.0.0-alpha33.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha33.apk
# 7f7663c3538a16e7bfd4f999f4eab9f4f5906909271a39ad47dc15aeb004ba87
```

Windows (cmd): `copy /b QVK-alpha33.apk.part0+QVK-alpha33.apk.part1+QVK-alpha33.apk.part2 QVK-8.100-qvk-3.0.0-alpha33.apk`

Пакет `ru.qmods.qvk`, versionCode 23832, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
