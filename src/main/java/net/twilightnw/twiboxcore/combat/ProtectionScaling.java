package net.twilightnw.twiboxcore.combat;

public record ProtectionScaling(int vanillaTotalLevelCap,
                                double reductionPerExcessLevel,
                                double maximumExtraReduction) {
    public ProtectionScaling {
        vanillaTotalLevelCap = Math.max(0, vanillaTotalLevelCap);
        reductionPerExcessLevel = clamp(reductionPerExcessLevel, 0.0, 0.02);
        maximumExtraReduction = clamp(maximumExtraReduction, 0.0, 0.75);
    }

    public double extraReduction(int totalProtectionLevel) {
        int excess = Math.max(0, totalProtectionLevel - vanillaTotalLevelCap);
        return Math.min(maximumExtraReduction, excess * reductionPerExcessLevel);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
