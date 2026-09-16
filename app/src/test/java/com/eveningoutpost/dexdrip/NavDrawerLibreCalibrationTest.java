package com.eveningoutpost.dexdrip;

import android.content.Intent;

import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.Calibration;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Adversarial verification of claim C2 (drawer reachability) at the level the user actually
 * experiences: {@link NavDrawerBuilder} with the "Libre2 Patched" collector.
 * <p>
 * The merge inserts a Libre branch at {@code NavDrawerBuilder.java:75-79}: with an active sensor
 * and calibration enabled the drawer gets an "Add Calibration" entry pointing at
 * {@link AddCalibration}; in {@code no_calibration} mode the old Dexcom chain must run instead
 * (no Add Calibration entry from the new branch); non-Libre collectors must not be affected.
 */
public class NavDrawerLibreCalibrationTest extends RobolectricTestWithConfig {

    private static final String CAL_MODE_PREF = "calibrate_external_libre_2_algorithm_type";
    private static final String COLLECTOR_PREF = "dex_collection_method";
    private static final String BLUKON_PREF = "external_blukon_algorithm";

    @Before
    public void resetState() {
        BgReading.deleteALL();
        Calibration.deleteAll();
        Sensor.deleteAll();
        Pref.removeItem(CAL_MODE_PREF);
        Pref.removeItem(BLUKON_PREF);
        Pref.setBoolean("I_understand", true);
    }

    @After
    public void clearPrefs() {
        Pref.removeItem(CAL_MODE_PREF);
        Pref.removeItem(COLLECTOR_PREF);
        Pref.removeItem(BLUKON_PREF);
        Pref.removeItem("I_understand");
    }

    /** C2: LibreReceiver + active sensor + calibrate_raw → reachable "Add Calibration". */
    @Test
    public void libreReceiverWithCalibrationEnabled_showsAddCalibration() {
        createMockSensorWithReadings();
        Pref.setString(COLLECTOR_PREF, "LibreReceiver");
        Pref.setString(CAL_MODE_PREF, "calibrate_raw");

        final NavDrawerBuilder drawer = new NavDrawerBuilder(xdrip.getAppContext());

        assertWithMessage("drawer offers the Add Calibration label")
                .that(drawer.nav_drawer_options)
                .contains(xdrip.getAppContext().getString(R.string.add_calibration));
        assertWithMessage("the entry points at AddCalibration")
                .that(targetClasses(drawer)).contains(AddCalibration.class.getName());
        assertWithMessage("the Libre path must not offer the Dexcom initial calibration")
                .that(targetClasses(drawer)).doesNotContain(DoubleCalibrationActivity.class.getName());
    }

    /** C2: same setup in no_calibration mode → the new branch must not fire. */
    @Test
    public void libreReceiverWithNoCalibration_hasNoAddCalibrationEntry() {
        createMockSensorWithReadings();
        Pref.setString(COLLECTOR_PREF, "LibreReceiver");
        Pref.setString(CAL_MODE_PREF, "no_calibration");

        final NavDrawerBuilder drawer = new NavDrawerBuilder(xdrip.getAppContext());

        assertWithMessage("no Add Calibration entry in no_calibration mode")
                .that(targetClasses(drawer)).doesNotContain(AddCalibration.class.getName());
        assertWithMessage("no Add Calibration label in no_calibration mode")
                .that(drawer.nav_drawer_options)
                .doesNotContain(xdrip.getAppContext().getString(R.string.add_calibration));
    }

    /** C2: a Dexcom collector must not gain a Libre calibration entry. */
    @Test
    public void dexcomG6_doesNotGainLibreCalibrationEntry() {
        createMockSensorWithReadings();
        Pref.setString(COLLECTOR_PREF, "DexcomG6");
        Pref.setString(CAL_MODE_PREF, "calibrate_raw");

        final NavDrawerBuilder drawer = new NavDrawerBuilder(xdrip.getAppContext());

        assertWithMessage("DexcomG6 never routes through the Libre branch")
                .that(targetClasses(drawer)).doesNotContain(AddCalibration.class.getName());
    }

    // ===== Helpers ================================================================================

    private void createMockSensorWithReadings() {
        final Sensor sensor = new Sensor();
        sensor.started_at = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20);
        sensor.uuid = UUID.randomUUID().toString();
        sensor.save();

        for (int minutesAgo : new int[]{0, 5, 10}) {
            final BgReading reading = new BgReading();
            reading.raw_data = 130;
            reading.calculated_value = 130;
            reading.age_adjusted_raw_value = 130;
            reading.filtered_data = 130;
            reading.timestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutesAgo);
            reading.sensor = sensor;
            reading.uuid = UUID.randomUUID().toString();
            reading.save();
        }
    }

    private static List<String> targetClasses(NavDrawerBuilder builder) {
        final List<String> classes = new ArrayList<>();
        for (Intent intent : builder.nav_drawer_intents) {
            classes.add(intent.getComponent().getClassName());
        }
        return classes;
    }
}
