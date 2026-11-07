package se.dick.geolert;

/**
 * Pure logic, no Android calls: boundary crossings with hysteresis, and how long
 * to wait until the next position check.
 */
public final class ZoneLogic {

    /** How far past the boundary you must be before an alarm is re-armed. */
    public static final double HYSTERESIS_M = 30;

    /** Assumed top speed when working out how soon a zone could be reached (≈126 km/h). */
    public static final double MAX_SPEED_MPS = 35;

    /** Never check more often than this… */
    public static final long FLOOR_MS = 30_000;
    /** …except close to a boundary, so a bus stop alarm is not 400 m late. */
    public static final long NEAR_FLOOR_MS = 10_000;
    public static final double NEAR_BOUNDARY_M = 500;

    /** Moved more than this = "on the move": check every 30 s. */
    public static final double MOVING_DISTANCE_M = 100;
    /** Stay in moving mode this long after the last 100 m step. */
    public static final long MOVING_HOLD_MS = 3 * 60_000;
    public static final long MOVING_INTERVAL_MS = 30_000;

    private ZoneLogic() {
    }

    /** Result of evaluating one alarm against one position. */
    public static final class Step {
        public final int newState;
        public final boolean fire;

        Step(int newState, boolean fire) {
            this.newState = newState;
            this.fire = fire;
        }
    }

    /**
     * Arrive: fires on OUTSIDE → INSIDE (distance ≤ r); re-armed when distance > r + 30.
     * Leave:  fires on INSIDE → OUTSIDE (distance > r); re-armed when distance < r − 30.
     * UNKNOWN (new, edited or re-activated alarm) only records the side, never fires.
     */
    public static Step evaluate(int state, boolean arrive, double distanceM, double radiusM) {
        boolean inside = distanceM <= radiusM;
        switch (state) {
            case Alarm.STATE_OUTSIDE:
                if (arrive) {
                    return inside ? new Step(Alarm.STATE_INSIDE, true) : new Step(state, false);
                }
                // Leave alarm waiting to be re-armed
                return distanceM < radiusM - HYSTERESIS_M
                        ? new Step(Alarm.STATE_INSIDE, false) : new Step(state, false);
            case Alarm.STATE_INSIDE:
                if (!arrive) {
                    return inside ? new Step(state, false) : new Step(Alarm.STATE_OUTSIDE, true);
                }
                // Arrive alarm waiting to be re-armed
                return distanceM > radiusM + HYSTERESIS_M
                        ? new Step(Alarm.STATE_OUTSIDE, false) : new Step(state, false);
            default:
                return new Step(inside ? Alarm.STATE_INSIDE : Alarm.STATE_OUTSIDE, false);
        }
    }

    /**
     * Time until the next check.
     *
     * @param ceilingMs          the user setting "Check position at least every…"
     * @param nearestBoundaryM   distance to the closest zone edge of any active alarm, minus GPS accuracy
     *                           (Double.MAX_VALUE if there are none)
     * @param moving             true while in moving mode
     */
    public static long nextIntervalMs(long ceilingMs, double nearestBoundaryM, boolean moving) {
        long interval = ceilingMs;
        double d = Math.max(0, nearestBoundaryM);
        if (d < Double.MAX_VALUE / 2) {
            interval = Math.min(interval, (long) (d / MAX_SPEED_MPS * 1000));
        }
        if (moving) interval = Math.min(interval, MOVING_INTERVAL_MS);
        long floor = d < NEAR_BOUNDARY_M ? NEAR_FLOOR_MS : FLOOR_MS;
        return Math.max(floor, Math.min(interval, ceilingMs));
    }

    /** Great-circle distance in meters. */
    public static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
