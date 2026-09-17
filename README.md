# xDrip+ RU custom: FreeStyle Libre 2 (Gen2 / RU) + калибровки

Кастомная сборка [xDrip+](https://github.com/NightscoutFoundation/xDrip) для сенсоров **FreeStyle Libre 2 российского региона (Gen2)**, которые работают через мост Juggluco, плюс рабочие калибровки для Libre.

Если коротко: xDrip сам Gen2 читать не умеет (у этого поколения сенсоров новая криптография, открытой реализации нет). Сенсор читает Juggluco, а эта сборка xDrip принимает его данные и даёт график, тревоги, калибровки и выгрузку.

## Что добавлено к стоку

1. **Распознавание Gen2** (ветка `ru-gen2`): сенсоры Gen2 (RU / Eastern / ROW, семейства `0x2b / 0x2c / 0x76`) распознаются как отдельный тип, и NFC-скан такого сенсора завершается понятным сообщением (используйте мост Juggluco) вместо ошибочного "libre1 failsafe".
2. **Калибровки для Libre** (ветка `libre2-calibration`): пункт "Add Calibration" доступен в меню для Libre-источников и применяется ко всем путям данных источника "Libre2 Patched" (включая scan/history, которые в стоке калибровку игнорировали). Калибровка Libre только смещением: наклон 1, смещение в пределах -40..+20 mg/dL (так устроено в xDrip, это не ограничение сборки).

Dexcom и нативные пути не затронуты; путь Libre 2 EU работает как в стоке.

## Скачать и установить

APK: [последний релиз](https://github.com/HyperDodo/xdrip-ru-custom/releases/latest)

- файл `xdrip-ru-gen2-calibration.apk`, sha256 `8dcb4973252392c749385498e6199eebe2d4fca638ff972d6198c38bc6f4d5cb`
- versionName `718d082-ru-custom-2026.09.16`
- подписан локальным ключом: поверх официального xDrip+ не установится. Если нужна история, сделай в старом приложении Export DB, а в новом после установки Import DB.

Пошагово (Gen2 RU):

1. Удали официальный xDrip+ (подписи разные, иначе Android откажет).
2. Установи APK, разреши установку из неизвестных источников.
3. Выдай xDrip и Juggluco разрешения: уведомления, "Устройства поблизости" (Bluetooth), и поставь "без ограничений" в настройках батареи для обоих приложений.
4. Juggluco (juggluco.nl): подключи сенсор (один NFC-скан), включи трансляцию "Patched Libre", на запрос пакета укажи `com.eveningoutpost.dexdrip`.
5. xDrip: источник данных "Libre2 Patched", затем меню -> Start sensor -> ответь "Not today".
6. Через пару минут появятся показания. Калибровка: меню -> Add Calibration.

Полная инструкция со всеми деталями и решением проблем: **[docs/GUIDE.md](docs/GUIDE.md)**.

## Ветки

- `ru-custom`: дефолтная. Финальная сборка, оба патча влиты. Тег `ru-custom-2026.09.16` указывает на коммит, из которого собран APK (`718d082`).
- `ru-gen2`: патч распознавания Gen2 отдельно.
- `libre2-calibration`: патч калибровок отдельно.
- `verify-tests`: unit-тесты (20/20 в production-equivalent режиме).
- `master`: база, NightscoutFoundation/xDrip @ `e1407ec` (Sep 2026).

## Документация

- [docs/GUIDE.md](docs/GUIDE.md): полный гайд (установка, Juggluco, калибровки, AAPS, решение проблем).
- [docs/INSTALL-AND-SETUP.md](docs/INSTALL-AND-SETUP.md): исходная инструкция по установке и настройке.
- [docs/verification-report.md](docs/verification-report.md): отчёт независимой верификации.
- [docs/PATCHES.txt](docs/PATCHES.txt): список изменённых файлов.

## Сборка из исходников

Требования: JDK 17 и Android SDK (platform 34, build-tools 35 или 36).

```
./gradlew.bat :app:assembleDebug            # Windows
./gradlew :app:assembleDebug                # Linux / macOS
```

Release: `:app:assembleProdRelease` (подпись настраивается через untracked `app/local.gradle`). Первая сборка занимает около 13 минут.

## Оговорки

- DIY-сборка, не медицинское изделие. Не связана с Abbott. Подтверждай показания глюкометром, особенно на низких значениях.
- Gen2 не читается напрямую и не будет без открытой реализации протокола; без Juggluco эта сборка для Gen2 бесполезна.
- Живой end-to-end прогон на реальном сенсоре не выполнялся (в среде сборки сенсора не было); проверка была по коду, тестам и содержимому APK.
- Лицензия: GPLv3. Оригинальный код принадлежит Nightscout Foundation и контрибьюторам xDrip+.

## English TL;DR

Custom xDrip+ build for FreeStyle Libre 2 Gen2 (Russian region) sensors. xDrip cannot read Gen2 directly (new crypto, no open implementation), so data comes from the Juggluco "Patched Libre" broadcast into the "Libre2 Patched" data source. This build adds clean Gen2 detection (clear message instead of a failed read) and makes user calibrations reachable and effective for Libre data (offset-only: slope 1, offset clamped to -40..+20 mg/dL). APK in Releases; full guide (Russian) in docs/GUIDE.md. GPLv3, DIY use only, not affiliated with Abbott.
