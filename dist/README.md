# QVK 8.100-qvk-3.0.0-alpha40

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-alpha40.apk.part0 QVK-alpha40.apk.part1 > QVK-8.100-qvk-3.0.0-alpha40.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha40.apk
# a48dd1d33ea03dfd581c30e7e6061069bae331301d7c3900a1882556cdc5ed11
```

Windows (cmd): `copy /b QVK-alpha40.apk.part0+QVK-alpha40.apk.part1 QVK-8.100-qvk-3.0.0-alpha40.apk`

Пакет `ru.qmods.qvk`, versionCode 23839, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
