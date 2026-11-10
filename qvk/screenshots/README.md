# Скриншоты экранов QVK 3.0 без устройства

Robolectric (NATIVE graphics) рендерит экраны QVK 3.0 на JVM: центр настроек (главная, все 9 разделов, поиск),
QVK Music, плеер в макетах Modern / CoverFlow / Classic. Так каждое изменение интерфейса проверяется по картинке
до сборки APK. Экраны самого VK так не отрисовать — их нужно проверять на устройстве.

Подготовка (после `python3 qvk/build.py`, переменная `QVK_CACHE` указывает на кэш сборки):

```bash
cd qvk/screenshots
(cd "$QVK_CACHE/mod-classes" && jar cf "$OLDPWD/qvk3.jar" .)            # новый код QVK 3.0
d2j-dex2jar.sh -o qvkcode.jar classes21.dex                              # код QVK 2.6.18 из базового APK
curl -o monitor.aar https://dl.google.com/android/maven2/androidx/test/monitor/1.6.1/monitor-1.6.1.aar && unzip -p monitor.aar classes.jar > monitor.jar
mvn -q test        # PNG → qvk/screenshots/shots/
```
