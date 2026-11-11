# QVK 8.100-qvk-3.0.0-alpha19

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha19.apk.part0 QVK-alpha19.apk.part1 QVK-alpha19.apk.part2 > QVK-8.100-qvk-3.0.0-alpha19.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha19.apk
# f8c5cf911dbf72a9b0479bbabeef764100525695a9d385cf14ec121a10b360e2
```

Windows (cmd): `copy /b QVK-alpha19.apk.part0+QVK-alpha19.apk.part1+QVK-alpha19.apk.part2 QVK-8.100-qvk-3.0.0-alpha19.apk`

Пакет `ru.qmods.qvk`, versionCode 23818, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
