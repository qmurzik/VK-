# QVK 8.100-qvk-3.0.0-alpha28

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha28.apk.part0 QVK-alpha28.apk.part1 QVK-alpha28.apk.part2 > QVK-8.100-qvk-3.0.0-alpha28.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha28.apk
# c272803650134175bd98710848c35f0a487c1d4b42304e2095051e0c347ab4cd
```

Windows (cmd): `copy /b QVK-alpha28.apk.part0+QVK-alpha28.apk.part1+QVK-alpha28.apk.part2 QVK-8.100-qvk-3.0.0-alpha28.apk`

Пакет `ru.qmods.qvk`, versionCode 23827, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
