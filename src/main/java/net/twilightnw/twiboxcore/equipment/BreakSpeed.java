package net.twilightnw.twiboxcore.equipment;

final class BreakSpeed {
    private BreakSpeed() {
    }

    static double toAttributeModifier(double finalMultiplier) {
        if (!Double.isFinite(finalMultiplier)) {
            return -0.60;
        }
        return Math.max(0.01, Math.min(1.0, finalMultiplier)) - 1.0;
    }
}
