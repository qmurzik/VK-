# QVK 8.100-qvk-3.0.0-alpha27

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha27.apk.part0 QVK-alpha27.apk.part1 QVK-alpha27.apk.part2 > QVK-8.100-qvk-3.0.0-alpha27.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha27.apk
# 3575a98d1d23dc0dbde7cf764b3afe38c3fef9a4785899cacb231d2f6a2c922e
```

Windows (cmd): `copy /b QVK-alpha27.apk.part0+QVK-alpha27.apk.part1+QVK-alpha27.apk.part2 QVK-8.100-qvk-3.0.0-alpha27.apk`

Пакет `ru.qmods.qvk`, versionCode 23826, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
