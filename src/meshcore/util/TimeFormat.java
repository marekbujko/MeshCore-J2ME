package meshcore.util;

/**
 * Human-readable uptime and UTC clock strings without {@code Calendar} (CLDC-safe).
 */
public final class TimeFormat {

    private TimeFormat() {}

    /** e.g. {@code "3d 4h 12m 5s"} from total seconds. */
    public static String formatUptimeDHMS(long totalSec) {
        if (totalSec < 0L) {
            totalSec = 0L;
        }
        long d = totalSec / 86400L;
        long r = totalSec % 86400L;
        long h = r / 3600L;
        r %= 3600L;
        long m = r / 60L;
        long s = r % 60L;
        StringBuffer sb = new StringBuffer();
        sb.append(d);
        sb.append("d ");
        sb.append(h);
        sb.append("h ");
        sb.append(m);
        sb.append("m ");
        sb.append(s);
        sb.append('s');
        return sb.toString();
    }

    private static boolean isLeapYear(int y) {
        if ((y % 4) != 0) {
            return false;
        }
        if ((y % 100) != 0) {
            return true;
        }
        return (y % 400) == 0;
    }

    private static int daysInMonth(int y, int month1to12) {
        switch (month1to12) {
            case 2:
                return isLeapYear(y) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * UTC date-time from Unix epoch seconds, e.g. {@code "2024-04-04 12:30:45 UTC"}.
     */
    public static String formatEpochUtc(long epochSec) {
        if (epochSec < 0L) {
            return "(invalid)";
        }
        long days = epochSec / 86400L;
        long sod = epochSec % 86400L;
        if (sod < 0L) {
            sod += 86400L;
            days--;
        }
        int y = 1970;
        while (true) {
            int diy = isLeapYear(y) ? 366 : 365;
            if (days >= diy) {
                days -= diy;
                y++;
            } else {
                break;
            }
        }
        int mon = 1;
        while (true) {
            int dim = daysInMonth(y, mon);
            if (days >= dim) {
                days -= dim;
                mon++;
            } else {
                break;
            }
        }
        int day = (int) days + 1;
        int hh = (int) (sod / 3600L);
        int mm = (int) ((sod % 3600L) / 60L);
        int ss = (int) (sod % 60L);
        StringBuffer sb = new StringBuffer();
        sb.append(y);
        sb.append('-');
        if (mon < 10) {
            sb.append('0');
        }
        sb.append(mon);
        sb.append('-');
        if (day < 10) {
            sb.append('0');
        }
        sb.append(day);
        sb.append(' ');
        if (hh < 10) {
            sb.append('0');
        }
        sb.append(hh);
        sb.append(':');
        if (mm < 10) {
            sb.append('0');
        }
        sb.append(mm);
        sb.append(':');
        if (ss < 10) {
            sb.append('0');
        }
        sb.append(ss);
        sb.append(" UTC");
        return sb.toString();
    }
}
