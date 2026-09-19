package dev.xenoah.hud.core;

/** All tuning values in SI units, except explicitly marked display values. */
public final class Config {
    private Config() {}
    public static final double G = 9.80665;
    public static final int SENSOR_PERIOD_US = 5_000; // request 200 Hz; measure actual delivery
    public static final int TARGET_FPS = 60;
    public static final double G_FILTER_CUTOFF_HZ = 4.0;
    public static final double HUD_FILTER_TAU_S = 0.012;
    public static final double FUSION_KP = 0.8;
    public static final double FUSION_NORM_GATE = 0.06;
    public static final double FUSION_ANGLE_GATE_RAD = Math.toRadians(2.5);
    public static final long CALIBRATION_NS = 1_000_000_000L;
    public static final long CALIBRATION_TIMEOUT_NS = 15_000_000_000L;
    public static final long FRESH_NS = 150_000_000L;
    public static final long MAX_PAIR_SKEW_NS = 30_000_000L;
    public static final long MAX_POSE_PREDICTION_NS = 100_000_000L;
    public static final int CALIBRATION_MIN_SAMPLES = 20;
    public static final long CALIBRATION_MOTION_WINDOW_NS = 100_000_000L;
    // Window statistics, not a limit applied to every individual noisy sample.
    public static final double STILL_GYRO_RAD_S = 0.045;
    public static final double STILL_GYRO_STD = 0.025;
    // An OS pose window bounds real movement; raw gyro magnitude may include a residual bias.
    public static final double NATIVE_CALIBRATION_GYRO_STD = 0.075;
    public static final double MAX_NATIVE_GYRO_BIAS_RAD_S = 0.10;
    public static final int CALIBRATION_MIN_POSE_SAMPLES = 5;
    public static final double STILL_ACCEL_STD = 0.12;
    public static final double CALIBRATION_MAX_GYRO_RAD_S = 0.35;
    public static final double CALIBRATION_ACCEL_NORM_ERROR = 0.6;
    public static final double CALIBRATION_MAX_TILT_RAD = Math.toRadians(2);
    public static final double MAX_ACCEL_BIAS = 0.5;
    public static final double G_RANGE = 2.0;
    public static final int HUD_WIDTH = 480, HUD_HEIGHT = 400;
}
