# QVK 8.100-qvk-3.0.0-alpha24

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha24.apk.part0 QVK-alpha24.apk.part1 QVK-alpha24.apk.part2 > QVK-8.100-qvk-3.0.0-alpha24.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha24.apk
# 51b0f70193f76b4dd3d80f61c84999e920c1e77141fd8637ace432eb68906764
```

Windows (cmd): `copy /b QVK-alpha24.apk.part0+QVK-alpha24.apk.part1+QVK-alpha24.apk.part2 QVK-8.100-qvk-3.0.0-alpha24.apk`

Пакет `ru.qmods.qvk`, versionCode 23823, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
