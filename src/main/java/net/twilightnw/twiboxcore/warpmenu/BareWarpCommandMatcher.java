package net.twilightnw.twiboxcore.warpmenu;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Pure exact-match parser for configured bare command labels. */
final class BareWarpCommandMatcher {
    private static final int MAX_COMMAND_LENGTH = 64;
    private final Set<String> labels;

    BareWarpCommandMatcher(Collection<String> configuredLabels) {
        LinkedHashSet<String> accepted = new LinkedHashSet<>();
        if (configuredLabels != null) {
            for (String value : configuredLabels) {
                if (value == null) continue;
                String label = value.trim().toLowerCase(Locale.ROOT);
                if (label.startsWith("/")) label = label.substring(1);
                if (!label.isEmpty() && label.length() <= MAX_COMMAND_LENGTH
                        && label.matches("[a-z0-9:_-]+")) {
                    accepted.add(label);
                }
            }
        }
        labels = Set.copyOf(accepted);
    }

    boolean matches(String message) {
        if (message == null || message.length() > MAX_COMMAND_LENGTH + 2) return false;
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("/") || normalized.length() == 1) return false;
        return labels.contains(normalized.substring(1));
    }

    int labelCount() {
        return labels.size();
    }
}
