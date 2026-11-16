# QVK 8.100-qvk-3.0.0 — финальная версия

QVK закрывается: это последняя сборка.

APK весит 185 МБ, а GitHub не принимает в git файлы больше 100 МБ, поэтому он разрезан на 2 части.

Склеить и проверить:

```bash
cat QVK-3.0.0.apk.part0 QVK-3.0.0.apk.part1 > QVK-8.100-qvk-3.0.0.apk
sha256sum QVK-8.100-qvk-3.0.0.apk
# 5191ce1213726334c4251d29af5ad6ae48caaaaeae7dad5eb0dd532ef0fc10cb
```

Windows (cmd): `copy /b QVK-3.0.0.apk.part0+QVK-3.0.0.apk.part1 QVK-8.100-qvk-3.0.0.apk`

Пакет `ru.qmods.qvk`, versionCode 23846, ставится поверх QVK 2.6.18 и всех сборок 3.0 (подпись та же).
