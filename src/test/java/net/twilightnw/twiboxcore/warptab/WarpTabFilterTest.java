package net.twilightnw.twiboxcore.warptab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WarpTabFilterTest {
    private final WarpTabFilter filter = new WarpTabFilter(
            List.of("warp", "/EWARP", "essentials:warp"),
            List.of("enddenspawna", "netherdenspawna", "siberdenspawna"));

    @Test
    void removesOnlyExactHiddenFirstArguments() {
        List<String> input = List.of("end", "enddenspawna", "enddenspawnax", "netherdenspawna");
        WarpTabFilter.FilterResult<String> result = filter.filterStrings("/warp e", input);

        assertTrue(result.changed());
        assertEquals(List.of("end", "enddenspawnax"), result.suggestions());
        assertEquals(List.of("end", "enddenspawna", "enddenspawnax", "netherdenspawna"), input);
    }

    @Test
    void supportsConfiguredAliasesAndRootLocaleNormalization() {
        assertEquals(List.of("end"),
                filter.filterStrings("/EWARP e", List.of("end", "enddenspawna")).suggestions());
        assertEquals(List.of("end"), filter.filterStrings(
                "/essentials:warp e", List.of("end", "enddenspawna")).suggestions());
    }

    @Test
    void leavesSecondArgumentAndUnrelatedCommandsUntouched() {
        List<String> input = List.of("enddenspawna");
        WarpTabFilter.FilterResult<String> second = filter.filterStrings("/warp end N", input);
        WarpTabFilter.FilterResult<String> unrelated = filter.filterStrings("/msg e", input);

        assertFalse(second.changed());
        assertFalse(unrelated.changed());
        assertSame(input, second.suggestions());
        assertSame(input, unrelated.suggestions());
    }

    @Test
    void doesNotOwnRootCommandCompletionOrAllocateWhenNothingChanges() {
        List<String> input = List.of("end", "nether");
        WarpTabFilter.FilterResult<String> root = filter.filterStrings("/war", input);
        WarpTabFilter.FilterResult<String> unchanged = filter.filterStrings("/warp e", input);

        assertFalse(root.changed());
        assertFalse(unchanged.changed());
        assertSame(input, root.suggestions());
        assertSame(input, unchanged.suggestions());
    }

    @Test
    void rejectsMalformedConfigurationTokensWithoutRegexOrPrefixMatching() {
        WarpTabFilter hardened = new WarpTabFilter(
                List.of("warp", "warp injected", "x".repeat(65)),
                List.of("enddenspawna", "bad token", "x".repeat(129)));

        assertEquals(1, hardened.commandCount());
        assertEquals(1, hardened.hiddenArgumentCount());
        assertEquals(List.of("enddenspawnax"), hardened.filterStrings(
                "/warp e", List.of("enddenspawna", "enddenspawnax")).suggestions());
    }

    @Test
    void preservesRichSuggestionObjects() {
        record Rich(String value, String tooltip) { }
        Rich visible = new Rich("end", "visible tooltip");
        Rich hidden = new Rich("enddenspawna", "hidden tooltip");
        WarpTabFilter.FilterResult<Rich> result = filter.filter(
                "/warp e", List.of(visible, hidden), Rich::value);

        assertEquals(List.of(visible), result.suggestions());
        assertSame(visible, result.suggestions().getFirst());
    }

    @Test
    void isSafeForConcurrentAsyncStyleReads() {
        AtomicBoolean failed = new AtomicBoolean();

        IntStream.range(0, 10_000).parallel().forEach(index -> {
            WarpTabFilter.FilterResult<String> result = filter.filterStrings(
                    "/warp e", List.of("end", "enddenspawna"));
            if (!result.changed() || !result.suggestions().equals(List.of("end"))) {
                failed.set(true);
            }
        });

        assertFalse(failed.get());
    }
}
