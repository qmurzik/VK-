# QVK 8.100-qvk-3.0.0-alpha35

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha35.apk.part0 QVK-alpha35.apk.part1 QVK-alpha35.apk.part2 > QVK-8.100-qvk-3.0.0-alpha35.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha35.apk
# fe319617808bb4ac5ed1457fa0466ee9edeab7bbf038d3520545902883879ac8
```

Windows (cmd): `copy /b QVK-alpha35.apk.part0+QVK-alpha35.apk.part1+QVK-alpha35.apk.part2 QVK-8.100-qvk-3.0.0-alpha35.apk`

Пакет `ru.qmods.qvk`, versionCode 23834, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
