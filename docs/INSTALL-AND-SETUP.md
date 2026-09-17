# xDrip+ custom build: FreeStyle Libre 2 (Russian region) + calibrations

Status: DRAFT (APK filename, sha256 and test results are filled in after the build and verification pass).

## What this build is

- Base: NightscoutFoundation/xDrip, master, commit `e1407ec9` (Sep 2026).
- Patch 1 (branch `ru-gen2`): Libre 2 Gen2 (RU / "Eastern / Rest of World") sensors are now
  recognised as their own sensor type instead of being misread as Libre 1 ("libre1 failsafe").
  On an NFC scan of such a sensor xDrip now stops cleanly with a clear message instead of a
  confusing failed read.
- Patch 2 (branch `libre2-calibration`): user calibrations are reachable in the UI for Libre data
  sources and are applied to all data paths of the "Libre2 Patched" (bridge) data source, including
  the scan/history paths which previously bypassed calibration. Calibration for Libre is
  offset-only by design in xDrip: slope is pinned to 1 and the offset is clamped to
  -40 .. +20 mg/dL (`Calibration.Li2AppParameters`).
- Package name stays `com.eveningoutpost.dexdrip`, signed with a locally generated key
  (your machine only).
- Delivered APK: `xdrip-ru-gen2-calibration.apk` (staged in `C:/Users/jar1k/xdrip-out/`);
  21,626,989 bytes; sha256 `8dcb4973252392c749385498e6199eebe2d4fca638ff972d6198c38bc6f4d5cb`.
- versionName shown in the app: `718d082-ru-custom-2026.09.16` (identifies the exact source commit).
- Signer: `CN=xDrip RU custom build, OU=DIY, O=Personal, L=Moscow, C=RU`,
  SHA-256 fingerprint `7891521a53ab7dea4f68b07d2f4dd6b853bcd72efcdc78633083b2f8c916255c`.
- Build proof: `:app:assembleProdRelease` BUILD SUCCESSFUL (7m 4s); the patch is present in the
  APK (string resource `libre2_gen2_not_supported_use_bridge`, dex symbols `Libre2Gen2`,
  `isLibreCalibrationEnabled`, `insertLibreBridgeBg`).

## Which Libre 2 sensor works how

| Sensor generation | Example patchInfo | How it is read | Calibration in this build |
|---|---|---|---|
| Libre 2 EU / 2+ EU | `7F 0E 30/31`, `C6 09 31`, `9D 08 30`, `C5 09 30` | xDrip directly (with OOP2 as before) | yes (advanced setting, below) |
| Libre 2 Gen2 RU ("Eastern / ROW") | `2B 0A 39 08` | Not directly. Bridge: Juggluco -> "Patched Libre" broadcast -> xDrip data source "Libre2 Patched" | yes (this build makes it reachable and applies it) |
| Libre 2 Gen2 other regions | `2C 0A 3A`, `2B 0A 3A`, `0x76...` | same bridge route | yes |

Hard limitation, stated plainly: xDrip cannot read Libre 2 Gen2 sensors by itself. Gen2 uses a new
crypto generation (new NFC commands and a challenge/response + secure-session Bluetooth
authentication) that is not implemented in xDrip and has no open Android implementation. Apps that
work with Gen2 (Juggluco, Diabox) read the sensor themselves, usually by using Abbott's own
library; this build receives the data from such an app and adds xDrip features on top
(graph, alarms, upload, calibration, AAPS hand-off).

## Install

1. If the official xDrip+ is installed: uninstall it first. This build is signed with a different
   key, so Android will refuse to replace the store/nightly build. If you want to keep history:
   in the old app, hamburger menu -> Export DB, then import it in this build after installing
   (Import DB).
2. Install the APK (allow "install unknown apps" for the file manager/browser you use).
3. Grant: notifications, Nearby devices/Bluetooth, and set battery usage to unrestricted for both
   xDrip and the bridge app. Android background limits are the usual cause of gaps.

## Setup A: Libre 2 Gen2 (Russian region) sensor

1. Activate the sensor the normal way (FreeStyle reader or LibreLink). Gen2 RU sensors are expected
   to be activated by Abbott software; the bridge app connects to an already activated sensor.
2. Install Juggluco (juggluco.nl) and let it connect to the sensor: scan the sensor once with NFC,
   then leave Bluetooth on; Juggluco receives the minute values.
3. In Juggluco: settings -> glucose broadcasts -> enable "Patched Libre" (do not enable the xDrip
   broadcast for this purpose). When Juggluco asks for the xDrip package information, enter
   `com.eveningoutpost.dexdrip` and save.
4. In xDrip: set the data source to Libre -> "Libre2 Patched" (Settings/source wizard). Then
   hamburger menu -> Start sensor -> answer "Not today". This only tells xDrip that a new sensor is
   delivering; it does not touch the sensor. A sensor record is created once readings arrive.
5. Readings appear within a few minutes (the bridge values are averaged over a short window).
6. Optional, if the sensor allows only one Bluetooth connection: disable LibreLink's Bluetooth
   (remove its Nearby devices permission) so it cannot fight with Juggluco.

## Setup B: Libre 2 EU sensor (direct, unchanged from stock xDrip)

Install OOP2 ("Oopalgorithm2") as before and use the xDrip Libre2 data source with NFC start, as
described in the AAPS/ xDrip documentation. The EU path is unchanged by this build except that the
calibration entry is now always reachable for Libre sources (see below).

## Calibrations

1. Wait until at least one reading from the bridge exists (and the sensor record exists).
2. Hamburger menu -> "Add Calibration" -> enter your fingerstick value. In stock xDrip a fresh
   Libre install only offered "Initial calibration" (or nothing); this build always offers
   "Add Calibration" for Libre sources while calibration is enabled.
3. Settings (search for `calibrate` or find "OOP2 algorithm calibration"):
   - "Calibrate based on raw" (default): keeps the existing behaviour; for the bridge it also
     enables calibration of the bridged values.
   - "Calibrate based on glucose" (experimental): same effect for the bridge;
     semantically the matching mode for pre-processed values.
   - "No calibration": no new calibration is applied and the "Add Calibration" entry is hidden;
     bridge scan/history values are inserted as-is. (Stock behaviour of the realtime bridge path
     for an already existing calibration is unchanged.)
4. Behaviour you should expect (verified in code, not yet with a live sensor):
   - Libre calibration is offset-only: slope stays 1, offset is clamped to -40 .. +20 mg/dL.
   - The first calibration shifts future readings; after the second calibration the recent
     readings are recalculated; history rewriting depends on the "rewrite history" setting.
   - A fingerstick must be entered in mg/dL when the app is in mg/dL mode (40-400 range for the
     initial double calibration).
5. If you use AAPS and want to calibrate from AAPS: xDrip -> Settings -> Inter-app settings ->
   "Accept calibrations" ON. Keep "Upload treatments" OFF as the AAPS docs require.

## What was verified, and what was not

Adversarial verification (independent agent, report: `verification-report.md`), all against the
shipped APK and the merged source:

| Claim | Verdict | Key evidence |
|---|---|---|
| Gen2 sensors are recognised and aborted with a clear message (phone app) | confirmed | `LibreOOPAlgorithmTest` 8/8 green incl. `2B 0A 39 08 4B F7 -> Libre2Gen2`; `Libre2Gen2` present in the shipped dex |
| Calibrations reachable for Libre sources | confirmed | `NavDrawerLibreCalibrationTest` 3/3 (entry present in `calibrate_raw`, absent in `no_calibration`, Dexcom unaffected) |
| Calibrations change values on the bridge paths | confirmed | `LibreBridgeCalibrationTest` 7/7 (raw 130, calibrate 140, next insert 150 with the offset applied; `calibration_uuid` set) |
| No unrelated regression | confirmed | diff is exactly 9 files, +82/-5; Dexcom paths untouched; `no_calibration` chain unchanged |

Documented caveats (all pre-existing or accepted trade-offs, none a regression):
- The gate also enables the calibration entry for LimiTTer-type Libre collectors (wider than just
  the bridge source).
- In `no_calibration` mode the realtime bridge path still applies a leftover calibration (stock
  behaviour; the new scan/history funnel does not).
- In calibration mode, history gap-filling (spline) is not applied to history points (per-point
  calibrated inserts instead).
- The Wear module ships the Gen2 sensor-type table but not the NFC-scan guard/message (the string
  resource does not exist there).
- Calibration on the bridge is a second stage on top of the values already calculated by the
  bridge app (offset-only for Libre, as designed).

Not verified (no sensor, phone, Juggluco or OOP2 available here):
- A live end-to-end run: NFC scan of a real Gen2 tag, Juggluco broadcast into xDrip, alarms,
  Nightscout upload, and the visible graph effect of a fingerstick calibration.
- The wear module's runtime path.

This is a DIY build, not a medical device. Confirm readings with fingersticks before acting on
them, especially lows and dosing decisions.

## Русская краткая инструкция

1. Активируйте сенсор в LibreLink (или считывателе FreeStyle), как обычно.
2. Установите Juggluco (juggluco.nl), отсканируйте сенсор по NFC, дальше Juggluco читает его по Bluetooth.
3. Juggluco: настройки -> трансляции глюкозы -> включите "Patched Libre" (трансляцию xDrip включать не нужно). На запрос имени пакета xDrip укажите `com.eveningoutpost.dexdrip` и сохраните.
4. В xDrip: источник данных -> Libre -> "Libre2 Patched". Затем меню -> "Запустить сенсор" -> ответьте "Not today" (это только регистрация сенсора в xDrip).
5. Через несколько минут появятся показания. Калибровка: меню -> "Добавить калибровку", введите значение с глюкометра.
6. В настройках (поиск "калибров") оставьте "Калибровка на основе raw" (или "на основе глюкозы"): оба режима включают калибровку для мостовых данных. Режим "Без калибровки" отключает её и убирает пункт меню.
7. Калибровка Libre работает только как смещение: наклон = 1, смещение в пределах от -40 до +20 мг/дл.
8. Если LibreLink мешает (сенсор допускает одно Bluetooth-подключение), отберите у LibreLink разрешение "Устройства поблизости".
