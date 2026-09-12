package net.twilightnw.twiboxcore.combat;

record LegacyArmorValues(double armor, double toughness, double knockbackResistance) {
    static LegacyArmorValues calculate(Piece piece, int protectionLevel) {
        int baseArmor = (protectionLevel / 5) + 1;
        boolean vipStep = protectionLevel % 5 == 3;
        double armor = switch (piece) {
            case HEAD, FEET -> baseArmor;
            case CHEST -> baseArmor + 5 + (vipStep ? 1 : 0);
            case LEGS -> baseArmor + 3 + (vipStep ? 1 : 0);
        };
        return new LegacyArmorValues(armor, 3.0, 0.1);
    }

    enum Piece { HEAD, CHEST, LEGS, FEET }
}
