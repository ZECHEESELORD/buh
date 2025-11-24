package sh.harold.fulcrum.message;

/**
 * Minimal facade mirroring the runtime API used by Quick Maths.
 */
public final class Message {
    private Message() {
    }

    public static MessageBuilder success(String identifier, Object... args) {
        return builder(MessageStyle.SUCCESS, identifier, args);
    }

    public static MessageBuilder info(String identifier, Object... args) {
        return builder(MessageStyle.INFO, identifier, args);
    }

    public static MessageBuilder debug(String identifier, Object... args) {
        return builder(MessageStyle.DEBUG, identifier, args);
    }

    public static MessageBuilder error(String identifier, Object... args) {
        return builder(MessageStyle.ERROR, identifier, args);
    }

    private static MessageBuilder builder(MessageStyle style, String identifier, Object... args) {
        String resolved = identifier;
        if (args != null && args.length > 0) {
            resolved = java.text.MessageFormat.format(identifier, args);
        }
        return new MessageBuilder(style, resolved);
    }
}
