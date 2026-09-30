package com.reya.alfheimheart.portal;

/** Mana amounts as players read them: 1 234 567 with thin gaps, or 1.2M when short is wanted. */
public final class Format {
    public static String mana(long mana) {
        String digits = Long.toString(Math.abs(mana));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return mana < 0 ? "-" + out : out.toString();
    }

    public static String shortMana(long mana) {
        if (mana >= 1_000_000_000L) return oneDecimal(mana / 1_000_000_000.0D) + "G";
        if (mana >= 1_000_000L) return oneDecimal(mana / 1_000_000.0D) + "M";
        if (mana >= 10_000L) return oneDecimal(mana / 1_000.0D) + "k";
        return Long.toString(mana);
    }

    private static String oneDecimal(double v) {
        long tenths = Math.round(v * 10.0D);
        return tenths % 10 == 0 ? Long.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }

    private Format() {
    }
}
