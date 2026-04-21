package meshcore.maps;

public final class MapMercator {

    private static final double PI = 3.141592653589793;
    private static final double LN2 = 0.6931471805599453;

    private MapMercator() {}

    /** Natural log (CLDC 1.1 Math has no log). */
    private static double ln(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        int n = 0;
        double v = x;
        while (v >= 2.0) {
            v *= 0.5;
            n++;
        }
        while (v < 1.0) {
            v *= 2.0;
            n--;
        }
        double u = v - 1.0;
        double sum = 0.0;
        double uk = u;
        for (int k = 1; k <= 18; k++) {
            if ((k & 1) == 1) {
                sum += uk / (double) k;
            } else {
                sum -= uk / (double) k;
            }
            uk *= u;
        }
        return LN2 * (double) n + sum;
    }

    public static void latLonToPixel(double latDeg, double lonDeg, int z, long[] outXY) {
        double latRad = latDeg * PI / 180.0;
        double world = 256.0 * (double) (1 << z);
        double x = (lonDeg + 180.0) / 360.0 * world;
        double sinLat = Math.sin(latRad);
        double ratio = (1.0 + sinLat) / (1.0 - sinLat);
        double y = (1.0 - ln(ratio) / (2.0 * PI)) / 2.0 * world;
        outXY[0] = (long) x;
        outXY[1] = (long) y;
    }

    public static int wrapTileX(int x, int z) {
        int n = 1 << z;
        int m = x % n;
        if (m < 0) {
            m += n;
        }
        return m;
    }

    public static int clampTileY(int y, int z) {
        int n = 1 << z;
        if (y < 0) {
            return 0;
        }
        if (y >= n) {
            return n - 1;
        }
        return y;
    }

    /** WGS84 equator meters per pixel at zoom 0 for a 256px world width (Web Mercator). */
    private static final double MPP_Z0_EQUATOR = 156543.03392804097;

    /** e^x; CLDC-friendly (split recursion + Taylor on [0, 0.5]). */
    private static double exp(double x) {
        if (x < 0.0) {
            return 1.0 / exp(-x);
        }
        if (x > 0.5) {
            double h = exp(x * 0.5);
            return h * h;
        }
        double sum = 1.0;
        double term = 1.0;
        for (int k = 1; k <= 22; k++) {
            term *= x / (double) k;
            sum += term;
        }
        return sum;
    }

    /** arcsin(x), |x|<=1; CLDC {@link Math} has no {@code asin}. */
    private static double asin(double x) {
        if (x < -1.0) {
            x = -1.0;
        } else if (x > 1.0) {
            x = 1.0;
        }
        if (x < 0.0) {
            return -asin(-x);
        }
        // Half-angle: asin(x)=pi/2-2*asin(sqrt((1-x)/2)); inner arg in (0,0.5] for x in (0.5,1].
        // Avoid asin(sqrt(1-x*x)): at x=1/sqrt(2) that equals x and recurses forever.
        if (x > 0.5) {
            return PI / 2.0 - 2.0 * asin(Math.sqrt((1.0 - x) / 2.0));
        }
        double x2 = x * x;
        double t = x;
        double s = x;
        for (int n = 1; n <= 26; n++) {
            t *= x2 * (double) ((2 * n - 1) * (2 * n - 1)) / (double) ((2 * n) * (2 * n + 1));
            s += t;
        }
        return s;
    }

    /**
     * Ground meters per pixel at the given latitude and zoom (horizontal scale, Web Mercator).
     */
    public static double metersPerPixelAtLat(double latDeg, int z) {
        double latRad = latDeg * PI / 180.0;
        double scale = MPP_Z0_EQUATOR / (double) (1 << z);
        return scale * Math.cos(latRad);
    }

    /**
     * Latitude in degrees for a Web Mercator world Y pixel (same space as {@link #latLonToPixel}).
     */
    public static double worldPixelYToLatDeg(double worldPixelY, int z) {
        double world = 256.0 * (double) (1 << z);
        double lnRatio = 2.0 * PI * (1.0 - 2.0 * worldPixelY / world);
        double ratio = exp(lnRatio);
        double sinLat = (ratio - 1.0) / (ratio + 1.0);
        if (sinLat > 1.0) {
            sinLat = 1.0;
        } else if (sinLat < -1.0) {
            sinLat = -1.0;
        }
        return asin(sinLat) * 180.0 / PI;
    }
}
