package net.twilightnw.twiboxcore.shopbridge;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, side-effect-free parser for FancyNpcs Shopkeepers links. */
public final class ShopkeeperRemoteActionResolver {
    static final int MAX_ACTIONS = 64;
    static final int MAX_VALUE_LENGTH = 256;
    private static final Pattern REMOTE_COMMAND = Pattern.compile(
            "(?i)^[ \\t]*/?shopkeepers?[ \\t]+remote[ \\t]+"
                    + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
                    + "[ \\t]+\\{player}[ \\t]*$");

    private ShopkeeperRemoteActionResolver() {
    }

    public static Optional<UUID> resolve(Collection<ActionDescriptor> actions) {
        if (actions == null || actions.isEmpty() || actions.size() > MAX_ACTIONS) {
            return Optional.empty();
        }
        UUID resolved = null;
        for (ActionDescriptor action : actions) {
            if (action == null || !"console_command".equalsIgnoreCase(action.actionName())) {
                continue;
            }
            String value = action.value();
            if (value == null || value.length() > MAX_VALUE_LENGTH) {
                continue;
            }
            Matcher matcher = REMOTE_COMMAND.matcher(value);
            if (!matcher.matches()) {
                continue;
            }
            UUID candidate;
            try {
                candidate = UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (resolved != null && !resolved.equals(candidate)) {
                return Optional.empty();
            }
            resolved = candidate;
        }
        return Optional.ofNullable(resolved);
    }

    public record ActionDescriptor(String actionName, String value) {
    }
}
