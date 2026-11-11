# QVK 8.100-qvk-3.0.0-alpha23

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha23.apk.part0 QVK-alpha23.apk.part1 QVK-alpha23.apk.part2 > QVK-8.100-qvk-3.0.0-alpha23.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha23.apk
# a7681a5a84b4d151ce9341a4bbd7d3f6e790b798f4a60a56796c8052ff2fb457
```

Windows (cmd): `copy /b QVK-alpha23.apk.part0+QVK-alpha23.apk.part1+QVK-alpha23.apk.part2 QVK-8.100-qvk-3.0.0-alpha23.apk`

Пакет `ru.qmods.qvk`, versionCode 23822, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
