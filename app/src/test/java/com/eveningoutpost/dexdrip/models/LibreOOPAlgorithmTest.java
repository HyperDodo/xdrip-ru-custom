package com.eveningoutpost.dexdrip.models;

import com.eveningoutpost.dexdrip.RobolectricTestWithConfig;
import com.eveningoutpost.dexdrip.models.LibreOOPAlgorithm.SensorType;

import org.junit.Test;

import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Adversarial verification (claim C1) of the custom "Libre 2 Gen2 (RU) recognition" patch.
 * <p>
 * The patch adds {@code SensorType.Libre2Gen2} (value 7) and makes
 * {@link LibreOOPAlgorithm#getSensorType(byte[])} return it whenever the patchInfo family byte
 * ({@code SensorInfo[0]}) is one of {@code 0x2b / 0x2c / 0x76} — the families DiaBLE classifies as
 * {@code .libre2Gen2} ({@code DiaBLE/Libre.swift:15-18}), with {@code 2B 0A 39 08} the RU sensor of
 * xDrip issue #3438 ({@code DiaBLE/Libre.swift:68-73}).
 * <p>
 * The test also pins the regression table from the design document: every previously known
 * patchInfo triple must keep its old classification, unknown values must still fall back to
 * {@code Libre1}, and a 24-byte patchInfo (Libre 3) must still win over the family-byte check.
 * <p>
 * Extends {@link RobolectricTestWithConfig} rather than being a bare JVM test: the unknown-type
 * fallback calls {@code UserError.Log.e()}, which writes an ActiveAndroid row and therefore needs
 * the Robolectric app context (only {@code android.util.Log} would return defaults).
 */
public class LibreOOPAlgorithmTest extends RobolectricTestWithConfig {

    // ===== Claim C1: Gen2 families map to the new SensorType =====================================

    /** The exact RU sensor from xDrip issue #3438: patchInfo 2B 0A 39 08 4B F7. */
    @Test
    public void russianGen2PatchInfo_mapsToLibre2Gen2() {
        assertSensorType(new int[]{0x2b, 0x0a, 0x39, 0x08, 0x4b, 0xf7},
                SensorType.Libre2Gen2, "RU Libre 2 Gen2 2B 0A 39 08");
    }

    /** All three families the design patch recognises: 0x2B (RU/LatAm), 0x2C (US 2+), 0x76 (Gen2). */
    @Test
    public void gen2FamilyBytes_allMapToLibre2Gen2() {
        assertSensorType(new int[]{0x2b, 0x0a, 0x3a, 0x08, 0x00, 0x00},
                SensorType.Libre2Gen2, "Libre 2+ LA 2B 0A 3A 08 (DiaBLE Libre.swift:71)");
        assertSensorType(new int[]{0x2c, 0x0a, 0x3a, 0x02, 0x00, 0x00},
                SensorType.Libre2Gen2, "Libre 2+ US 2C 0A 3A 02 (DiaBLE Libre.swift:70)");
        assertSensorType(new int[]{0x76, 0x0a, 0x39, 0x08, 0x00, 0x00},
                SensorType.Libre2Gen2, "family 0x76 (DiaBLE Libre.swift:15)");
    }

    /** C1: the new type is a distinct enum constant (it must NOT be an alias of Libre2). */
    @Test
    public void libre2Gen2_isDistinctConstantWithValue7() {
        assertWithMessage("Libre2Gen2 must not be an alias of Libre2")
                .that(SensorType.Libre2Gen2).isNotEqualTo(SensorType.Libre2);
        assertWithMessage("new constant keeps the design value 7")
                .that(SensorType.Libre2Gen2.value).isEqualTo(7);
        // existing constants must not have been renumbered by the addition
        assertWithMessage("Libre1 value").that(SensorType.Libre1.value).isEqualTo(0);
        assertWithMessage("Libre1New value").that(SensorType.Libre1New.value).isEqualTo(1);
        assertWithMessage("LibreUS14Day value").that(SensorType.LibreUS14Day.value).isEqualTo(2);
        assertWithMessage("Libre2 value").that(SensorType.Libre2.value).isEqualTo(3);
        assertWithMessage("LibreProH value").that(SensorType.LibreProH.value).isEqualTo(4);
        assertWithMessage("Libre2Plus value").that(SensorType.Libre2Plus.value).isEqualTo(5);
        assertWithMessage("Libre3 value").that(SensorType.Libre3.value).isEqualTo(6);
    }

    /** C1: the external OOP2 algorithm must never be asked to decode a Gen2 payload. */
    @Test
    public void isDecodeableData_falseForAllGen2Families() {
        assertWithMessage("RU Gen2 must not be decodeable")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{0x2b, 0x0a, 0x39, 0x08, 0x4b, (byte) 0xf7}))
                .isFalse();
        assertWithMessage("0x2C family must not be decodeable")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{0x2c, 0x0a, 0x3a, 0x02})).isFalse();
        assertWithMessage("0x76 family must not be decodeable")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{0x76, 0x0a, 0x39, 0x08})).isFalse();
    }

    /** C1 regression: the types that xDrip CAN decode keep isDecodeableData() == true. */
    @Test
    public void isDecodeableData_unchangedForSupportedTypes() {
        assertWithMessage("Libre 2 9d 08 30")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{(byte) 0x9d, 0x08, 0x30})).isTrue();
        assertWithMessage("Libre 2 7f 0e 30")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{0x7f, 0x0e, 0x30})).isTrue();
        assertWithMessage("Libre 2+ c6 09 31")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{(byte) 0xc6, 0x09, 0x31})).isTrue();
        assertWithMessage("Libre US 14 day e5 00 03")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{(byte) 0xe5, 0x00, 0x03})).isTrue();
        assertWithMessage("Libre 1 must stay non-decodeable")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{(byte) 0xdf, 0x00, 0x00})).isFalse();
        assertWithMessage("unknown must stay non-decodeable")
                .that(LibreOOPAlgorithm.isDecodeableData(new byte[]{0x12, 0x34, 0x56})).isFalse();
    }

    // ===== Claim C1 regression: the old classification table is untouched ========================

    /** Every patchInfo triple listed in the verification plan must still map to its old type. */
    @Test
    public void knownPatchInfos_keepTheirOldClassification() {
        assertSensorType(new int[]{0x9d, 0x08, 0x30}, SensorType.Libre2, "Libre 2 9d 08 30");
        assertSensorType(new int[]{0xc5, 0x09, 0x30}, SensorType.Libre2, "Libre 2 c5 09 30");
        assertSensorType(new int[]{0xc6, 0x09, 0x31}, SensorType.Libre2Plus, "Libre 2+ c6 09 31");
        assertSensorType(new int[]{0x7f, 0x0e, 0x30}, SensorType.Libre2, "Libre 2 7f 0e 30");
        assertSensorType(new int[]{0x7f, 0x0e, 0x31}, SensorType.Libre2Plus, "Libre 2+ 7f 0e 31");
        assertSensorType(new int[]{0xdf, 0x00, 0x00}, SensorType.Libre1, "Libre 1 df 00 00");
        assertSensorType(new int[]{0xa2, 0x08, 0x00}, SensorType.Libre1New, "Libre 1 New a2 08 00");
        assertSensorType(new int[]{0xe5, 0x00, 0x03}, SensorType.LibreUS14Day, "Libre US e5 00 03");
        assertSensorType(new int[]{0xe6, 0x00, 0x03}, SensorType.LibreUS14Day, "Libre US e6 00 03");
        assertSensorType(new int[]{0x70, 0x00, 0x10}, SensorType.LibreProH, "Libre Pro H 70 00 10");
    }

    // ===== Edge cases the patch could have broken =================================================

    /** A 24-byte patchInfo is a Libre 3 and must keep winning over the new family check. */
    @Test
    public void twentyFourBytePatchInfo_stillMapsToLibre3() {
        final byte[] zeros = new byte[24];
        assertSensorType(zeros, SensorType.Libre3, "24 zero bytes");

        // even when a 24-byte payload happens to start with a Gen2 family byte the Libre 3
        // length check comes first (the patch inserts the family check *after* it)
        final byte[] gen2Looking = new byte[24];
        gen2Looking[0] = 0x2b;
        gen2Looking[1] = 0x0a;
        gen2Looking[2] = 0x39;
        assertSensorType(gen2Looking, SensorType.Libre3,
                "24 bytes starting 2B 0A 39 -> Libre3 length check takes precedence");
    }

    /** Unknown values must still fall back to Libre1 (the documented failsafe). */
    @Test
    public void unknownAndNullPatchInfo_stillFallBackToLibre1() {
        assertSensorType(new int[]{0x12, 0x34, 0x56}, SensorType.Libre1, "unknown 12 34 56");
        assertWithMessage("null patchInfo falls back to Libre1")
                .that(LibreOOPAlgorithm.getSensorType(null)).isEqualTo(SensorType.Libre1);
    }

    // ===== Helpers =================================================================================

    private static void assertSensorType(int[] patchInfo, SensorType expected, String description) {
        assertWithMessage(description)
                .that(LibreOOPAlgorithm.getSensorType(asBytes(patchInfo)))
                .isEqualTo(expected);
    }

    private static void assertSensorType(byte[] patchInfo, SensorType expected, String description) {
        assertWithMessage(description)
                .that(LibreOOPAlgorithm.getSensorType(patchInfo))
                .isEqualTo(expected);
    }

    private static byte[] asBytes(int[] values) {
        final byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
