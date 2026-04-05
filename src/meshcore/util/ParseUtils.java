package meshcore.util;

/**
 * Number parsing utilities.
 */
public final class ParseUtils {

    private ParseUtils() {}

    public static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Parse decimal like "869.618" to raw (869618). Value * 1000. */
    public static long parseFreqBw(String s, long def) {
        if (s == null) return def;
        s = s.trim();
        if (s.length() == 0) return def;
        int dot = s.indexOf('.');
        try {
            if (dot < 0) {
                long whole = Long.parseLong(s);
                return whole * 1000;
            }
            long whole = Long.parseLong(s.substring(0, dot));
            String frac = s.substring(dot + 1);
            long f = 0;
            if (frac.length() > 0) {
                if (frac.length() > 3) frac = frac.substring(0, 3);
                f = Long.parseLong(frac);
                for (int i = frac.length(); i < 3; i++) f *= 10;
            }
            return whole * 1000 + f;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Parse a decimal degrees string (e.g. "-12.345678") to fixed-point * 1E6.
     * Returns {@code def} if empty or invalid.
     */
    public static int parseCoordDegreesToE6(String s, int def) {
        if (s == null) {
            return def;
        }
        s = s.trim();
        if (s.length() == 0) {
            return def;
        }
        boolean neg = false;
        int i = 0;
        if (s.charAt(0) == '-') {
            neg = true;
            i = 1;
        } else if (s.charAt(0) == '+') {
            i = 1;
        }
        try {
            String rest = s.substring(i);
            int dot = rest.indexOf('.');
            String wstr = dot < 0 ? rest : rest.substring(0, dot);
            if (wstr.length() == 0) {
                wstr = "0";
            }
            long wholeMag = Long.parseLong(wstr);
            long frac = 0;
            int fracDigits = 0;
            if (dot >= 0 && dot + 1 < rest.length()) {
                String f = rest.substring(dot + 1);
                if (f.length() > 6) {
                    f = f.substring(0, 6);
                }
                fracDigits = f.length();
                if (fracDigits > 0) {
                    frac = Long.parseLong(f);
                }
            }
            for (int k = fracDigits; k < 6; k++) {
                frac *= 10;
            }
            long mag = wholeMag * 1000000L + frac;
            long contrib = neg ? -mag : mag;
            if (contrib > 2147483647L || contrib < -2147483648L) {
                return def;
            }
            return (int) contrib;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
