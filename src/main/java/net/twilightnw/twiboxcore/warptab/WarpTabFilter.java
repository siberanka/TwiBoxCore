package net.twilightnw.twiboxcore.warptab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Pure, allocation-conscious filtering for first-argument warp suggestions. */
final class WarpTabFilter {
    private static final int MAX_COMMAND_LENGTH = 64;
    private static final int MAX_ARGUMENT_LENGTH = 128;

    private final Set<String> commandLabels;
    private final Set<String> hiddenFirstArguments;
    private final List<String> visibleFirstArguments;

    WarpTabFilter(Collection<String> commandLabels, Collection<String> hiddenFirstArguments,
                  Collection<String> visibleFirstArguments) {
        this.commandLabels = normalize(commandLabels, true, MAX_COMMAND_LENGTH);
        this.hiddenFirstArguments = normalize(hiddenFirstArguments, false, MAX_ARGUMENT_LENGTH);
        this.visibleFirstArguments = List.copyOf(normalizeOrdered(
                visibleFirstArguments, false, MAX_ARGUMENT_LENGTH));
    }

    FilterResult<String> filterStrings(String buffer, List<String> suggestions) {
        return filter(buffer, suggestions, Function.identity());
    }

    <T> FilterResult<T> filter(String buffer, List<T> suggestions, Function<T, String> valueExtractor) {
        if (suggestions.isEmpty() || !isFirstArgumentOfConfiguredCommand(buffer)) {
            return FilterResult.unchanged(suggestions);
        }

        ArrayList<T> filtered = null;
        for (int index = 0; index < suggestions.size(); index++) {
            T suggestion = suggestions.get(index);
            String value = valueExtractor.apply(suggestion);
            boolean hidden = value != null
                    && hiddenFirstArguments.contains(value.toLowerCase(Locale.ROOT));
            if (hidden) {
                if (filtered == null) {
                    filtered = new ArrayList<>(suggestions.size());
                    filtered.addAll(suggestions.subList(0, index));
                }
            } else if (filtered != null) {
                filtered.add(suggestion);
            }
        }
        return filtered == null
                ? FilterResult.unchanged(suggestions)
                : new FilterResult<>(List.copyOf(filtered), true);
    }

    FilterResult<String> reconcileStrings(String buffer, List<String> suggestions,
                                          Predicate<String> maySeeCandidate) {
        return reconcile(buffer, suggestions, Function.identity(), Function.identity(), maySeeCandidate);
    }

    <T> FilterResult<T> reconcile(String buffer, List<T> suggestions,
                                  Function<T, String> valueExtractor,
                                  Function<String, T> valueFactory,
                                  Predicate<String> maySeeCandidate) {
        FilterResult<T> filtered = filter(buffer, suggestions, valueExtractor);
        String prefix = firstArgumentPrefix(buffer);
        if (prefix == null) {
            return filtered;
        }

        LinkedHashSet<String> existing = filtered.suggestions().stream()
                .map(valueExtractor)
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        ArrayList<T> result = null;
        for (String candidate : visibleFirstArguments) {
            if (!candidate.startsWith(prefix) || existing.contains(candidate)
                    || !maySeeCandidate.test(candidate)) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>(filtered.suggestions());
            }
            result.add(valueFactory.apply(candidate));
            existing.add(candidate);
        }
        return result == null
                ? filtered
                : new FilterResult<>(List.copyOf(result), true);
    }

    int commandCount() {
        return commandLabels.size();
    }

    int hiddenArgumentCount() {
        return hiddenFirstArguments.size();
    }

    int visibleArgumentCount() {
        return visibleFirstArguments.size();
    }

    private boolean isFirstArgumentOfConfiguredCommand(String buffer) {
        return firstArgumentPrefix(buffer) != null;
    }

    private String firstArgumentPrefix(String buffer) {
        if (buffer == null) {
            return null;
        }
        int separator = buffer.indexOf(' ');
        if (separator < 0) {
            return null;
        }
        String label = buffer.substring(0, separator);
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        if (!commandLabels.contains(label.toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (buffer.indexOf(' ', separator + 1) >= 0) {
            return null;
        }
        return buffer.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalize(Collection<String> values, boolean stripSlash, int maxLength) {
        return Set.copyOf(normalizeOrdered(values, stripSlash, maxLength));
    }

    private static Collection<String> normalizeOrdered(
            Collection<String> values, boolean stripSlash, int maxLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> normalize(value, stripSlash, maxLength))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String value, boolean stripSlash, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (stripSlash && normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty() || normalized.length() > maxLength
                || normalized.indexOf(' ') >= 0 || normalized.indexOf('\t') >= 0
                || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            return "";
        }
        return normalized;
    }

    record FilterResult<T>(List<T> suggestions, boolean changed) {
        private static <T> FilterResult<T> unchanged(List<T> suggestions) {
            return new FilterResult<>(suggestions, false);
        }
    }
}
