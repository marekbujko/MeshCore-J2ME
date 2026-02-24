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
}
