package com.eveningoutpost.dexdrip.models;

import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.LibreAlarmReceiver;
import com.eveningoutpost.dexdrip.RobolectricTestWithConfig;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Adversarial verification of claim C3 (calibration effectiveness for the Libre 2 bridge paths).
 * <p>
 * Modelled on {@link CalibrationTest}. Everything runs on the real product methods
 * ({@link LibreAlarmReceiver#insertLibreBridgeBg}) — no reproductions of the maths — so a failure
 * here is either a product defect or an honest statement about the merged code, never a test
 * artefact.
 * <p>
 * The insert helper is the single funnel added by the merge:
 * <ul>
 *   <li>{@code LibreReceiver.java:90} (LIBRE2_SCAN realtime) calls it directly,</li>
 *   <li>{@code LibreReceiver.java:109-117} (LIBRE2_SCAN history) loops it per history point when a
 *       calibration is active, otherwise keeps the original {@code insertFromHistory} call,</li>
 *   <li>the LIBRE2_BG path ({@code LibreReceiver.processValues} → {@code BgReading.bgReadingInsertLibre2})
 *       is not routed through it by this merge; that pre-existing path is asserted separately
 *       because claim C3 names it too.</li>
 * </ul>
 * Post-calibration inserts are placed 21 minutes after the calibration reading on purpose: within
 * 20 minutes the shared {@code BgReading.create()} "double calibration" raw override
 * ({@code BgReading.java:596}) re-runs {@code calculate_w_l_s()} and would blur the isolated
 * offset assertion (pre-existing behaviour, shared with the Dexcom raw path).
 */
public class LibreBridgeCalibrationTest extends RobolectricTestWithConfig {

    private static final String CAL_MODE_PREF = "calibrate_external_libre_2_algorithm_type";
    private static final String COLLECTOR_PREF = "dex_collection_method";
    private static final String BLUKON_PREF = "external_blukon_algorithm";

    /** LibreReceiver dedup period = sample period 5 min − 1/6 (DexCollectionType.java:416-419). */
    private static final long DEDUP_MARGIN = 250_000L;
    /** 21 minutes: outside the 20-minute double-calibration raw-override window. */
    private static final long AFTER_OVERRIDE_WINDOW = TimeUnit.MINUTES.toMillis(21);

    @Before
    public void resetState() {
        BgReading.deleteALL();
        Calibration.deleteAll();
        Sensor.deleteAll();
    }

    @After
    public void clearPrefs() {
        Pref.removeItem(CAL_MODE_PREF);
        Pref.removeItem(COLLECTOR_PREF);
        Pref.removeItem(BLUKON_PREF);
    }

    // ===== C3: no calibration present → value stays raw, never 0 / invisible =====================

    @Test
    public void bridgeInsertWithoutCalibration_keepsCalculatedValueEqualToRaw() {
        createMockSensor();
        enableLibreBridge();

        final long now = System.currentTimeMillis();
        LibreAlarmReceiver.insertLibreBridgeBg(130, now, DEDUP_MARGIN, false);

        final BgReading last = BgReading.last();
        assertThat(last).isNotNull();
        assertWithMessage("raw value stored on the glucose scale").that(last.raw_data).isEqualTo(130.0);
        assertWithMessage("calculated value equals raw (never 0 / invisible)")
                .that(last.calculated_value).isEqualTo(130.0);
        assertThat(last.calculated_value).isNotEqualTo(0.0);
    }

    // ===== C3: calibration present → offset-only slope 1 / clamp −40..+20 ========================

    @Test
    public void bridgeInsertAfterCalibration_appliesLibreOffset() {
        createMockSensor();
        enableLibreBridge();

        final long t0 = System.currentTimeMillis();
        LibreAlarmReceiver.insertLibreBridgeBg(130, t0, DEDUP_MARGIN, false);

        final Calibration created = Calibration.create(140, RuntimeEnvironment.application);
        assertThat(created).isNotNull();

        final Calibration valid = Calibration.lastValid();
        assertThat(valid).isNotNull();
        assertWithMessage("calibration took the inserted raw as its raw_value")
                .that(valid.raw_value).isWithin(0.01).of(130.0);
        assertWithMessage("Libre offset-only calibration pins slope to 1")
                .that(valid.slope).isWithin(0.001).of(1.0);
        assertWithMessage("intercept = bg − raw = +10 (Li2 clamp permits −40..+20)")
                .that(valid.intercept).isWithin(0.001).of(10.0);

        final long t1 = t0 + AFTER_OVERRIDE_WINDOW;
        LibreAlarmReceiver.insertLibreBridgeBg(140, t1, DEDUP_MARGIN, false);

        final BgReading last = BgReading.last();
        assertThat(last.timestamp).isEqualTo(t1);
        assertWithMessage("raw stored as fed").that(last.raw_data).isEqualTo(140.0);
        assertWithMessage("value shifted by the +10 mg/dL offset").that(last.calculated_value)
                .isWithin(1.5).of(150.0);
        assertWithMessage("calibrated route tags the reading with the calibration")
                .that(last.calibration_uuid).isEqualTo(valid.uuid);
    }

    // ===== C3: no_calibration mode → the new helper leaves bridge values untouched ===============

    @Test
    public void noCalibrationMode_doesNotApplyOffsetToBridgeInsert() {
        createMockSensor();
        enableLibreBridge();

        final long t0 = System.currentTimeMillis();
        LibreAlarmReceiver.insertLibreBridgeBg(130, t0, DEDUP_MARGIN, false);
        Calibration.create(140, RuntimeEnvironment.application); // a calibration exists…
        Pref.setString(CAL_MODE_PREF, "no_calibration");         // …but the mode says don't use it

        final long t1 = t0 + AFTER_OVERRIDE_WINDOW;
        LibreAlarmReceiver.insertLibreBridgeBg(145, t1, DEDUP_MARGIN, false);

        final BgReading last = BgReading.last();
        assertWithMessage("fallback path stores the value untouched").that(last.calculated_value)
                .isEqualTo(145.0);
        assertThat(last.raw_data).isEqualTo(145.0);
        assertWithMessage("fallback path does not tag a calibration").that(last.calibration_uuid).isNull();
    }

    // ===== C3: the LIBRE2_BG path (pre-existing bgReadingInsertLibre2) ===========================

    @Test
    public void libre2BgPath_withCalibration_appliesOffset() {
        createMockSensor();
        enableLibreBridge();

        final long t0 = System.currentTimeMillis();
        LibreAlarmReceiver.insertLibreBridgeBg(130, t0, DEDUP_MARGIN, false);
        Calibration.create(140, RuntimeEnvironment.application);
        final Calibration valid = Calibration.lastValid();
        assertThat(valid).isNotNull();

        final BgReading inserted = BgReading.bgReadingInsertLibre2(150, t0 + AFTER_OVERRIDE_WINDOW, 150);

        assertThat(inserted).isNotNull();
        assertThat(inserted.raw_data).isEqualTo(150.0);
        assertWithMessage("LIBRE2_BG path applies slope 1 + the +10 offset")
                .that(inserted.calculated_value).isWithin(1.5).of(160.0);
        assertThat(inserted.calibration_uuid).isEqualTo(valid.uuid);
    }

    @Test
    public void libre2BgPath_withoutCalibration_storesValueAsIs() {
        createMockSensor();
        enableLibreBridge();

        final BgReading inserted = BgReading.bgReadingInsertLibre2(150, System.currentTimeMillis(), 150);

        assertThat(inserted).isNotNull();
        assertThat(inserted.raw_data).isEqualTo(150.0);
        assertThat(inserted.calculated_value).isEqualTo(150.0);
    }

    /**
     * Adversarial counter-evidence, deliberately pinned: the LIBRE2_BG path applies a leftover
     * calibration even in {@code no_calibration} mode. This is pre-existing upstream behaviour
     * (identical code in master e1407ec9 — {@code BgReading.bgReadingInsertLibre2} takes
     * {@code Calibration.lastValid()} unconditionally), not introduced by this build; the design
     * document's optional "Hunk 5" that would have fixed it was NOT merged. Claim C3's
     * "no_calibration leaves values untouched" is therefore true only for the new
     * {@code insertLibreBridgeBg} funnel, not for the whole bridge surface.
     */
    @Test
    public void noCalibrationMode_leftoverCalibrationStillAppliedOnLibre2BgPath_preExisting() {
        createMockSensor();
        enableLibreBridge();

        final long t0 = System.currentTimeMillis();
        LibreAlarmReceiver.insertLibreBridgeBg(130, t0, DEDUP_MARGIN, false);
        Calibration.create(140, RuntimeEnvironment.application);
        final Calibration valid = Calibration.lastValid();
        assertThat(valid).isNotNull();

        Pref.setString(CAL_MODE_PREF, "no_calibration");
        final BgReading inserted = BgReading.bgReadingInsertLibre2(150, t0 + AFTER_OVERRIDE_WINDOW, 150);

        assertThat(inserted).isNotNull();
        assertThat(inserted.raw_data).isEqualTo(150.0);
        assertWithMessage("leftover calibration still shifts the value in no_calibration mode")
                .that(inserted.calculated_value).isWithin(1.5).of(160.0);
        assertThat(inserted.calibration_uuid).isEqualTo(valid.uuid);
    }

    // ===== C3: dedup on the calibrated route =====================================================

    @Test
    public void dedup_repeatedInsertAtSameTimestamp_createsOneReading() {
        createMockSensor();
        enableLibreBridge();

        final long t0 = System.currentTimeMillis();
        LibreAlarmReceiver.insertLibreBridgeBg(130, t0, DEDUP_MARGIN, false);
        Calibration.create(140, RuntimeEnvironment.application);

        final long t1 = t0 + AFTER_OVERRIDE_WINDOW;
        final int before = countReadings();
        LibreAlarmReceiver.insertLibreBridgeBg(140, t1, DEDUP_MARGIN, false);
        LibreAlarmReceiver.insertLibreBridgeBg(140, t1, DEDUP_MARGIN, false);

        assertWithMessage("second insert at the same timestamp must be dropped")
                .that(countReadings() - before).isEqualTo(1);
        assertWithMessage("the one inserted reading still carries the offset")
                .that(BgReading.last().calculated_value).isWithin(1.5).of(150.0);
    }

    // ===== Helpers ================================================================================

    private void enableLibreBridge() {
        Pref.setString(COLLECTOR_PREF, "LibreReceiver");
        Pref.setString(CAL_MODE_PREF, "calibrate_raw");
        Pref.setBoolean(BLUKON_PREF, false);
    }

    private static int countReadings() {
        return new Select().from(BgReading.class).execute().size();
    }

    private Sensor createMockSensor() {
        final Sensor mockSensor = new Sensor();
        mockSensor.started_at = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20);
        mockSensor.uuid = UUID.randomUUID().toString();
        mockSensor.save();
        return mockSensor;
    }
}