package meshcore.protocol;

import java.io.IOException;

import meshcore.net.FrameTransport;
import meshcore.util.ParseUtils;
import meshcore.util.SHA256;

public final class MeshProtocolClient {

    private MeshProtocolClient() {}

    public static void sendChannelMessage(FrameTransport transport, int channelIndex, String msg, int epochSeconds) throws IOException {
        byte[] tb = msg.getBytes("UTF-8");
        byte[] f = new byte[7 + tb.length];
        f[0] = (byte) ProtocolConstants.CMD_SEND_CHANNEL_MSG;
        f[1] = 0;
        f[2] = (byte) (channelIndex & 0xFF);
        FrameTransport.writeUint32LE(f, 3, epochSeconds);
        System.arraycopy(tb, 0, f, 7, tb.length);
        transport.sendFrame(f);
    }

    public static void sendDirectMessage(FrameTransport transport, byte[] key, String msg, int epochSeconds) throws IOException {
        byte[] tb = msg.getBytes("UTF-8");
        byte[] f = new byte[13 + tb.length];
        f[0] = (byte) ProtocolConstants.CMD_SEND_TXT_MSG;
        f[1] = 0;
        f[2] = 0;
        FrameTransport.writeUint32LE(f, 3, epochSeconds);
        System.arraycopy(key, 0, f, 7, 6);
        System.arraycopy(tb, 0, f, 13, tb.length);
        transport.sendFrame(f);
    }

    public static void sendGetContacts(FrameTransport transport) throws IOException {
        transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_CONTACTS});
    }

    public static void sendGetChannels(FrameTransport transport) throws IOException {
        for (int i = 0; i < ProtocolConstants.MAX_CHANNEL_SLOTS; i++) {
            transport.sendFrame(new byte[]{
                (byte) ProtocolConstants.CMD_GET_CHANNEL,
                (byte) (i & 0xFF)
            });
        }
    }

    public static void sendSetChannel(FrameTransport transport, int channelIndex, String name, byte[] secretBytes) throws IOException {
        byte[] nb = name.getBytes("UTF-8");
        if (nb.length > 32) {
            byte[] trimmed = new byte[32];
            System.arraycopy(nb, 0, trimmed, 0, 32);
            nb = trimmed;
        }
        byte[] name32 = new byte[32];
        System.arraycopy(nb, 0, name32, 0, nb.length);
        byte[] secret = (secretBytes != null && secretBytes.length == 16)
                ? secretBytes : SHA256.channelSecret(name);
        byte[] f = new byte[2 + 32 + 16];
        f[0] = (byte) ProtocolConstants.CMD_SET_CHANNEL;
        f[1] = (byte) (channelIndex & 0xFF);
        System.arraycopy(name32, 0, f, 2, 32);
        System.arraycopy(secret, 0, f, 34, 16);
        transport.sendFrame(f);
    }

    public static void removeContact(FrameTransport transport, byte[] key) throws IOException {
        byte[] f = new byte[1 + 32];
        f[0] = (byte) ProtocolConstants.CMD_REMOVE_CONTACT;
        System.arraycopy(key, 0, f, 1, 32);
        transport.sendFrame(f);
    }

    public static void sendRefreshSettings(FrameTransport transport, String myName) throws IOException {
        transport.sendFrame(new byte[]{
            (byte) ProtocolConstants.CMD_DEVICE_QUERY,
            (byte) ProtocolConstants.APP_VER
        });
        byte[] nb = myName.getBytes("UTF-8");
        byte[] as = new byte[2 + 6 + nb.length];
        as[0] = (byte) ProtocolConstants.CMD_APP_START;
        as[1] = (byte) ProtocolConstants.APP_VER;
        System.arraycopy(nb, 0, as, 8, nb.length);
        transport.sendFrame(as);
        transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_BATT_STORAGE});
    }

    public static void sendGetBattery(FrameTransport transport) throws IOException {
        transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_BATT_STORAGE});
    }

    public static void sendGetStats(FrameTransport transport) throws IOException {
        transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_STATS, 0});
    }

    public static void sendGetDeviceTime(FrameTransport transport) throws IOException {
        transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_DEVICE_TIME});
    }

    public static int sendAdvert(FrameTransport transport, int advertType) throws IOException {
        int t = (advertType == ProtocolConstants.ADVERT_ZERO_HOP)
                ? ProtocolConstants.ADVERT_ZERO_HOP : ProtocolConstants.ADVERT_FLOOD;
        byte[] f = {(byte) ProtocolConstants.CMD_SEND_SELF_ADVERT, (byte) t};
        transport.sendFrame(f);
        return t;
    }

    public static void sendSyncNextMessage(FrameTransport transport) throws IOException {
        transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_SYNC_NEXT_MESSAGE});
    }

    public static long[] applySettingsAndBuildRadioFrames(SettingsScreenLike settings,
                                                          long currentFreq,
                                                          long currentBw,
                                                          int currentSf,
                                                          int currentCr,
                                                          int currentTxPwr,
                                                          FrameTransport transport) throws IOException {
        long nodeFreq = ParseUtils.parseFreqBw(settings.getFreq(), currentFreq);
        long nodeBw = ParseUtils.parseFreqBw(settings.getBw(), currentBw);
        int nodeSf = ParseUtils.parseInt(settings.getSf(), currentSf);
        int nodeCr = ParseUtils.parseInt(settings.getCr(), currentCr);
        int nodeTxPwr = ParseUtils.parseInt(settings.getTxPwr(), currentTxPwr);

        byte[] rp = new byte[11];
        rp[0] = (byte) ProtocolConstants.CMD_SET_RADIO_PARAMS;
        FrameTransport.writeUint32LE(rp, 1, nodeFreq);
        FrameTransport.writeUint32LE(rp, 5, nodeBw);
        rp[9] = (byte) nodeSf;
        rp[10] = (byte) nodeCr;
        transport.sendFrame(rp);

        transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_SET_RADIO_TX_PWR, (byte) nodeTxPwr});

        return new long[]{nodeFreq, nodeBw, nodeSf, nodeCr, nodeTxPwr};
    }

    // Minimal interface to decouple from concrete SettingsScreen implementation.
    public interface SettingsScreenLike {
        String getFreq();
        String getBw();
        String getSf();
        String getCr();
        String getTxPwr();
    }
}

