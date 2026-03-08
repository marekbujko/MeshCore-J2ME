package meshcore.ui;

public final class ChatMessage {
    public final String text;
    public final boolean fromMe;
    public final String sender;
    public final String timestamp;

    public ChatMessage(String text, boolean fromMe, String sender) {
        this(text, fromMe, sender, null);
    }

    public ChatMessage(String text, boolean fromMe, String sender, String timestamp) {
        this.text = text;
        this.fromMe = fromMe;
        this.sender = sender;
        this.timestamp = timestamp;
    }
}

