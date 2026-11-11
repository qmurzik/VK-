# QVK 8.100-qvk-3.0.0-alpha30

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha30.apk.part0 QVK-alpha30.apk.part1 QVK-alpha30.apk.part2 > QVK-8.100-qvk-3.0.0-alpha30.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha30.apk
# e27ec513bd322deab6f9dac380d2e61f2703d62a4b3b122019bfc009e2ad6491
```

Windows (cmd): `copy /b QVK-alpha30.apk.part0+QVK-alpha30.apk.part1+QVK-alpha30.apk.part2 QVK-8.100-qvk-3.0.0-alpha30.apk`

Пакет `ru.qmods.qvk`, versionCode 23829, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
