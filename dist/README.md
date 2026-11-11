# QVK 8.100-qvk-3.0.0-alpha31

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 3 части.

Склеить и проверить:

```bash
cat QVK-alpha31.apk.part0 QVK-alpha31.apk.part1 QVK-alpha31.apk.part2 > QVK-8.100-qvk-3.0.0-alpha31.apk
sha256sum QVK-8.100-qvk-3.0.0-alpha31.apk
# c8aadfe88f82272c53a2e783b8abefedf06d6ef601beafd71f6a2dd77cc786f8
```

Windows (cmd): `copy /b QVK-alpha31.apk.part0+QVK-alpha31.apk.part1+QVK-alpha31.apk.part2 QVK-8.100-qvk-3.0.0-alpha31.apk`

Пакет `ru.qmods.qvk`, versionCode 23830, ставится поверх QVK 2.6.18 (подпись та же). На устройстве не проверялась: см. `QVK_TEST_PLAN.md`.
