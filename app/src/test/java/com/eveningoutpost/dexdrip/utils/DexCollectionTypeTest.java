package com.eveningoutpost.dexdrip.utils;

import static com.google.common.truth.Truth.assertWithMessage;

import com.eveningoutpost.dexdrip.RobolectricTestWithConfig;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DexCollectionTypeTest extends RobolectricTestWithConfig {

    final String opt = "calibrate_external_libre_2_algorithm_type";
    final String opt2 = "external_blukon_algorithm";
    final String opt3 = "dex_collection_method";

    private void cleanup() {
        Pref.removeItem(opt);
        Pref.removeItem(opt2);
        Pref.removeItem(opt3);
    }

    @Before
    public void before() {
        cleanup();
    }

    @After
    public void after() {
        cleanup();
    }

    @Test
    public void isLibreOOPNonCalibratebleAlgorithmTest() {

        Pref.setString(opt,"no_calibration");
        assertWithMessage("no calibration matches").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isTrue();
        Pref.setString(opt,"calibrate_raw");
        assertWithMessage("calibrate raw matches").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isFalse();
        Pref.setString(opt,"calibrate_glucose");
        assertWithMessage("calibrate glucose matches").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isFalse();

        Pref.setBoolean(opt2,false);
        Pref.setString(opt,"no_calibration");
        assertWithMessage("no calibration matches 1").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isTrue();
        Pref.setString(opt,"calibrate_raw");
        assertWithMessage("calibrate raw matches 1").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isFalse();
        Pref.setString(opt,"calibrate_glucose");
        assertWithMessage("calibrate glucose matches 1").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isFalse();

        Pref.setBoolean(opt2,true);
        Pref.setString(opt,"no_calibration");
        assertWithMessage("no calibration matches 2 ").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isTrue();
        Pref.setString(opt,"calibrate_raw");
        assertWithMessage("calibrate raw matches 2").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isTrue();
        Pref.setString(opt,"calibrate_glucose");
        assertWithMessage("calibrate glucose matches 2").that(DexCollectionType.isLibreOOPNonCalibratebleAlgorithm(DexCollectionType.LimiTTer)).isTrue();

    }

    /**
     * Adversarial verification of the claim-C2 gate added by the custom build:
     * {@code isLibreCalibrationEnabled()} must be true for Libre collectors while the user has
     * left calibration on ({@code calibrate_raw} / {@code calibrate_glucose}), false for
     * {@code no_calibration}, false when OOP2 decoding is delegated to an external algorithm
     * ({@code external_blukon_algorithm}), and false for every non-Libre collector so no Dexcom
     * code path changes behaviour.
     */
    @Test
    public void isLibreCalibrationEnabledTest() {

        Pref.setBoolean(opt2, false);
        Pref.setString(opt, "calibrate_raw");

        assertWithMessage("LibreReceiver with calibrate_raw is calibratable")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.LibreReceiver)).isTrue();
        assertWithMessage("LibreAlarm (OOP2) with calibrate_raw is calibratable")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.LibreAlarm)).isTrue();
        // Documented scope note: hasLibre() covers every Libre collector, so the LimiTTer family
        // also gains the entry (design-libre2-calibration.md, "behaviour change to be aware of").
        assertWithMessage("LimiTTer also counts as Libre (documented scope expansion)")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.LimiTTer)).isTrue();
        assertWithMessage("DexcomG6 must be unaffected")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.DexcomG6)).isFalse();
        assertWithMessage("BluetoothWixel must be unaffected")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.BluetoothWixel)).isFalse();
        assertWithMessage("None must be unaffected")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.None)).isFalse();

        Pref.setString(opt, "no_calibration");
        assertWithMessage("no_calibration turns the Libre gate off")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.LibreReceiver)).isFalse();

        Pref.setString(opt, "calibrate_glucose");
        assertWithMessage("calibrate_glucose is calibratable")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.LibreReceiver)).isTrue();

        Pref.setString(opt, "calibrate_raw");
        Pref.setBoolean(opt2, true);
        assertWithMessage("external OOP2 algorithm (blukon) turns the Libre gate off")
                .that(DexCollectionType.isLibreCalibrationEnabled(DexCollectionType.LibreReceiver)).isFalse();

        // no-arg overload reads the active collector from the "dex_collection_method" preference
        Pref.setBoolean(opt2, false);
        Pref.setString(opt, "calibrate_raw");
        Pref.setString(opt3, "LibreReceiver");
        assertWithMessage("no-arg form follows the active LibreReceiver collector")
                .that(DexCollectionType.isLibreCalibrationEnabled()).isTrue();
        Pref.setString(opt3, "DexcomG6");
        assertWithMessage("no-arg form stays off for the DexcomG6 collector")
                .that(DexCollectionType.isLibreCalibrationEnabled()).isFalse();
    }
}