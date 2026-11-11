# QVK 8.100-qvk-3.0.0-alpha8

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha8.apk.part0 QVK-alpha8.apk.part1 QVK-alpha8.apk.part2 > QVK-8.100-qvk-3.0.0-alpha8.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha8.apk
# 1ded963f8395fa02676e0cccd415cb67a7cdc599027cbe105f334ad45864d758
```

Windows (cmd): `copy /b QVK-alpha8.apk.part0+QVK-alpha8.apk.part1+QVK-alpha8.apk.part2 QVK-8.100-qvk-3.0.0-alpha8.apk`

Пакет `ru.qmods.qvk`, versionCode 23807, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
