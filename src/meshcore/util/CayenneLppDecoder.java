package meshcore.util;

import meshcore.protocol.ProtocolConstants;

/**
 * Decodes Cayenne LPP payloads as produced by MeshCore / pycayennelpp
 * (see meshcore_py: pycayennelpp, lpp_json_encoder.my_lpp_types).
 * Wire format: [channel:1][type:1][value...] big-endian (matches smlng/pycayennelpp).
 * <p>
 * Types 0–3 match classic LPP; types 100+ are the extended set used on the wire
 * for illuminance, temperature, voltage (116 / 0x74), GPS (136), etc.
 * Classic myDevices types 4–18 are still decoded for older/other senders.
 */
public final class CayenneLppDecoder {

    private CayenneLppDecoder() {}

    /** Show all channels. */
    public static String decodeToText(byte[] data) {
        return decodeToText(data, -1);
    }

    /**
     * @param channelFilter if &gt;= 0, only lines for that LPP channel are included in the text
     *                      (records on other channels are still consumed from the buffer).
     *                      Use {@link ProtocolConstants#TELEM_CHANNEL_SELF} (1) for MeshCore self readings.
     */
    public static String decodeToText(byte[] data, int channelFilter) {
        if (data == null || data.length == 0) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        int i = 0;
        int n = data.length;
        while (i + 2 <= n) {
            int ch = data[i++] & 0xFF;
            int type = data[i++] & 0xFF;
            int need = valueSize(type);
            if (need < 0) {
                if (channelFilter < 0 || ch == channelFilter) {
                    sb.append("Ch ").append(ch).append(": unknown type 0x");
                    sb.append(hexByte(type)).append(" - raw tail: ");
                    sb.append(FrameUtils.bytesToHex(data, i - 2, n - (i - 2)));
                } else {
                    if (sb.length() == 0) {
                        sb.append("(stopped: unknown LPP type 0x").append(hexByte(type))
                                .append(" on channel ").append(ch).append(')');
                    }
                }
                return sb.toString();
            }
            if (i + need > n) {
                if (channelFilter < 0 || ch == channelFilter) {
                    sb.append("Ch ").append(ch).append(": truncated (need ").append(need).append(" B)");
                } else {
                    if (sb.length() == 0) {
                        sb.append("(stopped: truncated field on channel ").append(ch).append(')');
                    }
                }
                return sb.toString();
            }
            String line = decodeOne(ch, type, data, i);
            i += need;
            if (channelFilter < 0 || ch == channelFilter) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        if (i < n) {
            if (channelFilter < 0) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("? trailing ").append(n - i).append(" B: ");
                sb.append(FrameUtils.bytesToHex(data, i, n - i));
            } else if (sb.length() == 0) {
                sb.append("(no data on channel ").append(channelFilter).append(')');
            }
        } else if (channelFilter >= 0 && sb.length() == 0) {
            sb.append("(no data on channel ").append(channelFilter).append(')');
        }
        return sb.toString();
    }

    private static int valueSize(int type) {
        if (isPycayenneExtended(type)) {
            return pycayennePayloadSize(type);
        }
        return classicValueSize(type);
    }

    /** pycayennelpp LppType.__lpp_types (MeshCore wire format). */
    private static boolean isPycayenneExtended(int type) {
        return type == 100 || type == 101 || type == 102 || type == 103 || type == 104
                || type == 113 || type == 115 || type == 116 || type == 117 || type == 118
                || type == 120 || type == 121 || type == 122 || type == 125 || type == 128
                || type == 130 || type == 131 || type == 132 || type == 133 || type == 134
                || type == 135 || type == 136 || type == 142;
    }

    private static int pycayennePayloadSize(int type) {
        switch (type) {
            case 102:
            case 104:
            case 120:
            case 142:
                return 1;
            case 101:
            case 103:
            case 115:
            case 116:
            case 117:
            case 125:
            case 128:
            case 132:
                return 2;
            case 113:
            case 134:
                return 6;
            case 122:
            case 135:
                return 3;
            case 100:
            case 118:
            case 130:
            case 131:
            case 133:
                return 4;
            case 136:
                return 9;
            default:
                return -1;
        }
    }

    /** Classic myDevices LPP types 4–18 (not used on MeshCore extended wire). */
    private static int classicValueSize(int type) {
        switch (type) {
            case 0:
            case 1:
            case 5:
            case 7:
            case 17:
                return 1;
            case 2:
            case 3:
            case 4:
            case 6:
            case 9:
            case 13:
            case 14:
            case 15:
            case 18:
                return 2;
            case 12:
            case 16:
                return 4;
            case 8:
            case 10:
                return 6;
            case 11:
                return 9;
            default:
                return -1;
        }
    }

    private static String decodeOne(int ch, int type, byte[] b, int o) {
        if (isPycayenneExtended(type)) {
            return decodePycayenne(ch, type, b, o);
        }
        return decodeClassic(ch, type, b, o);
    }

    private static String decodePycayenne(int ch, int type, byte[] b, int o) {
        StringBuffer s = new StringBuffer();
        s.append("Ch ").append(ch).append(": ");
        switch (type) {
            case 100: {
                long v = readUInt32BE(b, o);
                s.append("Generic Sensor ").append(Long.toString(v));
                break;
            }
            case 101: {
                int v = readUInt16BE(b, o);
                s.append("Illuminance ").append(v).append(" lux");
                break;
            }
            case 102:
                s.append("Presence ").append(b[o] & 0xFF);
                break;
            case 103: {
                int v = readSignedBE(b, o, 2);
                s.append("Temperature ").append(fix1Signed(v, 10)).append(" C");
                break;
            }
            case 104: {
                int v = b[o] & 0xFF;
                s.append("Humidity ").append(humidityPercent(v)).append(" %");
                break;
            }
            case 113: {
                int x = readSignedBE(b, o, 2);
                int y = readSignedBE(b, o + 2, 2);
                int z = readSignedBE(b, o + 4, 2);
                s.append("Accelerometer x=").append(fix3Signed(x, 1000))
                        .append(" y=").append(fix3Signed(y, 1000))
                        .append(" z=").append(fix3Signed(z, 1000)).append(" G");
                break;
            }
            case 115: {
                int v = readUInt16BE(b, o);
                s.append("Barometer ").append(uintDiv10(v)).append(" hPa");
                break;
            }
            case 116: {
                int raw = readUInt16BE(b, o);
                double volts = meshcoreVoltageVolts(raw);
                s.append("Voltage ").append(formatDouble2(volts)).append(" V");
                break;
            }
            case 117: {
                int raw = readUInt16BE(b, o);
                double amps = meshcoreCurrentAmps(raw);
                s.append("Current ").append(formatDouble3(amps)).append(" A");
                break;
            }
            case 118: {
                long v = readUInt32BE(b, o);
                s.append("Frequency ").append(Long.toString(v)).append(" Hz");
                break;
            }
            case 120:
                s.append("Percentage ").append(b[o] & 0xFF).append('%');
                break;
            case 121: {
                int v = readSignedBE(b, o, 2);
                s.append("Altitude ").append(Integer.toString(v)).append(" m");
                break;
            }
            case 122: {
                int v = readSignedBE(b, o, 3);
                s.append("Load ").append(fix3Signed(v, 1000));
                break;
            }
            case 125: {
                int v = readUInt16BE(b, o);
                s.append("Concentration ").append(v);
                break;
            }
            case 128: {
                int v = readUInt16BE(b, o);
                s.append("Power ").append(v).append(" W");
                break;
            }
            case 130: {
                long v = readUInt32BE(b, o);
                s.append("Distance ").append(formatUintDiv1000(v)).append(" m");
                break;
            }
            case 131: {
                long v = readUInt32BE(b, o);
                s.append("Energy ").append(formatUintDiv1000(v)).append(" kWh");
                break;
            }
            case 132: {
                int v = readUInt16BE(b, o);
                s.append("Direction ").append(v).append(" deg");
                break;
            }
            case 133: {
                long t = readUInt32BE(b, o);
                s.append("Unix time ").append(Long.toString(t));
                break;
            }
            case 134: {
                int x = readSignedBE(b, o, 2);
                int y = readSignedBE(b, o + 2, 2);
                int z = readSignedBE(b, o + 4, 2);
                s.append("Gyrometer x=").append(fix2Signed(x, 100))
                        .append(" y=").append(fix2Signed(y, 100))
                        .append(" z=").append(fix2Signed(z, 100)).append(" deg/s");
                break;
            }
            case 135: {
                int r = b[o] & 0xFF;
                int g = b[o + 1] & 0xFF;
                int bl = b[o + 2] & 0xFF;
                s.append("Colour R=").append(r).append(" G=").append(g).append(" B=").append(bl);
                break;
            }
            case 136: {
                int lat = readSignedBE(b, o, 3);
                int lon = readSignedBE(b, o + 3, 3);
                int alt = readSignedBE(b, o + 6, 3);
                s.append("Location lat=").append(fix4Signed(lat, 10000))
                        .append(" lon=").append(fix4Signed(lon, 10000))
                        .append(" alt=").append(fix1Signed(alt, 100)).append(" m");
                break;
            }
            case 142:
                s.append("Switch ").append(b[o] & 0xFF);
                break;
            default:
                s.append("type ").append(type);
                break;
        }
        return s.toString();
    }

    /** meshcore_py lpp_format_val for voltage (116). */
    private static double meshcoreVoltageVolts(int rawU16) {
        double v = rawU16 / 100.0;
        if (v > 327.67) {
            v -= 655.36;
        }
        return v;
    }

    /** meshcore_py lpp_format_val for current (117). */
    private static double meshcoreCurrentAmps(int rawU16) {
        double v = rawU16 / 1000.0;
        if (v > 32.767) {
            v -= 65.536;
        }
        return v;
    }

    private static String formatDouble2(double v) {
        boolean neg = v < 0;
        if (neg) {
            v = -v;
        }
        int whole = (int) v;
        int frac = (int) ((v - whole) * 100.0 + 0.5);
        if (frac >= 100) {
            frac = 99;
        }
        String f = frac < 10 ? "0" + frac : Integer.toString(frac);
        return (neg ? "-" : "") + whole + "." + f;
    }

    private static String formatDouble3(double v) {
        boolean neg = v < 0;
        if (neg) {
            v = -v;
        }
        int whole = (int) v;
        int frac = (int) ((v - whole) * 1000.0 + 0.5);
        if (frac >= 1000) {
            frac = 999;
        }
        String f;
        if (frac < 10) {
            f = "00" + frac;
        } else if (frac < 100) {
            f = "0" + frac;
        } else {
            f = Integer.toString(frac);
        }
        return (neg ? "-" : "") + whole + "." + f;
    }

    private static String formatUintDiv1000(long v) {
        long whole = v / 1000;
        long frac = v % 1000;
        String f;
        if (frac < 10) {
            f = "00" + frac;
        } else if (frac < 100) {
            f = "0" + frac;
        } else {
            f = Long.toString(frac);
        }
        return whole + "." + f;
    }

    private static String decodeClassic(int ch, int type, byte[] b, int o) {
        StringBuffer s = new StringBuffer();
        s.append("Ch ").append(ch).append(": ");
        switch (type) {
            case 0:
                s.append("Digital In ").append(b[o] & 0xFF);
                break;
            case 1:
                s.append("Digital Out ").append(b[o] & 0xFF);
                break;
            case 2: {
                int v = readSignedBE(b, o, 2);
                s.append("Analog In ").append(fix2SignedCenti(v)).append(" (raw ").append(v).append(')');
                break;
            }
            case 3: {
                int v = readSignedBE(b, o, 2);
                s.append("Analog Out ").append(fix2SignedCenti(v));
                break;
            }
            case 4: {
                int v = readUInt16BE(b, o);
                s.append("Illuminance (classic) ").append(v).append(" lux");
                break;
            }
            case 5:
                s.append("Presence (classic) ").append(b[o] & 0xFF);
                break;
            case 6: {
                int v = readInt16BE(b, o);
                s.append("Temperature (classic) ").append(fix1Signed(v, 10)).append(" C");
                break;
            }
            case 7: {
                int v = b[o] & 0xFF;
                s.append("Humidity (classic) ").append(humidityPercent(v)).append(" %");
                break;
            }
            case 8: {
                int x = readInt16BE(b, o);
                int y = readInt16BE(b, o + 2);
                int z = readInt16BE(b, o + 4);
                s.append("Accel (classic) x=").append(fix3Signed(x, 1000))
                        .append(" y=").append(fix3Signed(y, 1000))
                        .append(" z=").append(fix3Signed(z, 1000));
                break;
            }
            case 9: {
                int v = readUInt16BE(b, o);
                s.append("Pressure (classic) ").append(uintDiv10(v)).append(" hPa");
                break;
            }
            case 10: {
                int x = readInt16BE(b, o);
                int y = readInt16BE(b, o + 2);
                int z = readInt16BE(b, o + 4);
                s.append("Gyro (classic) x=").append(fix2Signed(x, 100))
                        .append(" y=").append(fix2Signed(y, 100))
                        .append(" z=").append(fix2Signed(z, 100));
                break;
            }
            case 11: {
                int lat = readInt24BE(b, o);
                int lon = readInt24BE(b, o + 3);
                int alt = readInt16BE(b, o + 6);
                s.append("GPS (classic) lat=").append(fix4Signed(lat, 10000))
                        .append(" lon=").append(fix4Signed(lon, 10000))
                        .append(" alt=").append(alt).append(" m");
                break;
            }
            case 12: {
                long t = readUInt32BE(b, o);
                s.append("Unix time (classic) ").append(Long.toString(t));
                break;
            }
            case 13: {
                int v = readUInt16BE(b, o);
                s.append("Generic (classic) ").append(v);
                break;
            }
            case 14: {
                int v = readUInt16BE(b, o);
                s.append("Voltage (classic) ").append(fix2UnsignedCenti(v)).append(" V");
                break;
            }
            case 15: {
                int v = readUInt16BE(b, o);
                s.append("Current (classic) ").append(fix3UnsignedMilli(v)).append(" A");
                break;
            }
            case 16: {
                long v = readUInt32BE(b, o);
                s.append("Frequency (classic) ").append(Long.toString(v));
                break;
            }
            case 17:
                s.append("Percentage (classic) ").append(b[o] & 0xFF).append('%');
                break;
            case 18: {
                int v = readInt16BE(b, o);
                s.append("Altitude (classic) ").append(fix2Signed(v, 100)).append(" m");
                break;
            }
            default:
                s.append("type ").append(type);
                break;
        }
        return s.toString();
    }

    /** Big-endian unsigned int from 1–4 bytes. */
    private static int readUnsignedBE(byte[] b, int o, int len) {
        int v = 0;
        for (int i = 0; i < len; i++) {
            v = (v << 8) | (b[o + i] & 0xFF);
        }
        return v;
    }

    /** Big-endian signed int for 1–4 byte width (pycayennelpp __to_signed). */
    private static int readSignedBE(byte[] b, int o, int len) {
        int v = readUnsignedBE(b, o, len);
        int bits = len * 8;
        int signBit = 1 << (bits - 1);
        if (v >= signBit) {
            int mask = (1 << bits) - 1;
            v = -1 - (v ^ mask);
        }
        return v;
    }

    private static int readUInt16BE(byte[] b, int o) {
        return readUnsignedBE(b, o, 2);
    }

    private static int readInt16BE(byte[] b, int o) {
        return readSignedBE(b, o, 2);
    }

    private static int readInt24BE(byte[] b, int o) {
        return readSignedBE(b, o, 3);
    }

    private static long readUInt32BE(byte[] b, int o) {
        return ((long) readUnsignedBE(b, o, 4)) & 0xFFFFFFFFL;
    }

    private static String fix2UnsignedCenti(int value) {
        int whole = value / 100;
        int frac = value % 100;
        if (frac < 10) {
            return whole + ".0" + frac;
        }
        return whole + "." + frac;
    }

    private static String fix2SignedCenti(int value) {
        return fix2Signed(value, 100);
    }

    private static String fix3UnsignedMilli(int value) {
        int whole = value / 1000;
        int rem = value % 1000;
        int f1 = rem / 100;
        int f2 = (rem / 10) % 10;
        int f3 = rem % 10;
        return whole + "." + f1 + f2 + f3;
    }

    private static String fix1Signed(int value, int div) {
        boolean neg = value < 0;
        int v = neg ? -value : value;
        int whole = v / div;
        int frac = v % div;
        return (neg ? "-" : "") + whole + "." + frac;
    }

    private static String humidityPercent(int raw) {
        int whole = raw / 2;
        int frac = (raw % 2) * 5;
        return whole + "." + frac;
    }

    private static String uintDiv10(int v) {
        int whole = v / 10;
        int frac = v % 10;
        return whole + "." + frac;
    }

    private static String fix2Signed(int value, int div) {
        boolean neg = value < 0;
        int v = neg ? -value : value;
        int whole = v / div;
        int frac = v % div;
        if (div == 100) {
            if (frac < 10) {
                return (neg ? "-" : "") + whole + ".0" + frac;
            }
            return (neg ? "-" : "") + whole + "." + frac;
        }
        return (neg ? "-" : "") + whole;
    }

    private static String fix3Signed(int value, int div) {
        boolean neg = value < 0;
        int v = neg ? -value : value;
        int whole = v / div;
        int rem = v % div;
        int f1 = rem / 100;
        int f2 = (rem / 10) % 10;
        int f3 = rem % 10;
        return (neg ? "-" : "") + whole + "." + f1 + f2 + f3;
    }

    private static String fix4Signed(int value, int div) {
        boolean neg = value < 0;
        int v = neg ? -value : value;
        int whole = v / div;
        int frac = v % div;
        if (frac < 10) {
            return (neg ? "-" : "") + whole + ".000" + frac;
        }
        if (frac < 100) {
            return (neg ? "-" : "") + whole + ".00" + frac;
        }
        if (frac < 1000) {
            return (neg ? "-" : "") + whole + ".0" + frac;
        }
        return (neg ? "-" : "") + whole + "." + frac;
    }

    private static String hexByte(int t) {
        final char[] H = "0123456789abcdef".toCharArray();
        return "" + H[(t >> 4) & 0xF] + H[t & 0xF];
    }
}
