package meshcore.maps;

public final class MapTileUrl {

    private MapTileUrl() {}

    public static String format(String template, int z, int x, int y) {
        if (template == null) {
            return null;
        }
        String u = replaceAll(template, "#Z#", String.valueOf(z));
        u = replaceAll(u, "#X#", String.valueOf(x));
        u = replaceAll(u, "#Y#", String.valueOf(y));
        int sub = (x + y + z) & 3;
        u = replaceAll(u, "{s}", "mt" + sub);
        u = replaceAll(u, "{z}", String.valueOf(z));
        u = replaceAll(u, "{x}", String.valueOf(x));
        u = replaceAll(u, "{y}", String.valueOf(y));
        return u;
    }

    private static String replaceAll(String s, String pat, String val) {
        if (s == null || pat == null || val == null) {
            return s;
        }
        int pl = pat.length();
        StringBuffer out = new StringBuffer();
        int i = 0;
        while (i < s.length()) {
            int p = s.indexOf(pat, i);
            if (p < 0) {
                out.append(s.substring(i));
                break;
            }
            out.append(s.substring(i, p));
            out.append(val);
            i = p + pl;
        }
        return out.toString();
    }
}
