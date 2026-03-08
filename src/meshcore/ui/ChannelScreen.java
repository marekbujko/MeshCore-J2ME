package meshcore.ui;

import meshcore.util.HistoryStore;

/**
 * Channel chat screen for a specific channel, rendered as bubbles on a Canvas.
 */
public class ChannelScreen extends AbstractChatCanvas {

    private final int channelIndex;
    public ChannelScreen(AppController app, int channelIndex, String channelName,
                         StringBuffer channelBuf) {
        super(app, channelName, channelBuf);
        this.channelIndex = channelIndex;
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    protected void onBack() {
        app.showChannelListScreen();
    }

    protected void onSendMessage(final String msg) {
        new Thread(new Runnable() {
            public void run() {
                app.sendChannelMessage(channelIndex, msg);
            }
        }).start();
    }

    protected void onClearHistoryConfirmed() {
        app.clearChannelHistory(channelIndex);
    }

    protected boolean loadOlderHistoryBatch() {
        return HistoryStore.loadOlderChannelIntoBuffer(channelIndex, buf);
    }
}

