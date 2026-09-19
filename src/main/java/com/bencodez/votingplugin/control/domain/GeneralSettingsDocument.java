package com.bencodez.votingplugin.control.domain;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Bounded, lossless access to the small allow-list of top-level Config.yml
 * general settings. This deliberately edits scalar source spans rather than
 * dumping YAML, so comments, redactions, ordering, and unknown settings stay
 * byte-for-byte (as Java characters) unchanged.
 */
public final class GeneralSettingsDocument {
    private static final int MAX_DOCUMENT_CODE_POINTS = 512 * 1024;
    private static final int MAX_NESTING_DEPTH = 40;
    private static final int MAX_COLLECTION_ALIASES = 16;

    private static final List<String> KNOWN_KEYS = List.of(
            "ProcessRewards", "AutoCreateVoteSites", "ExtraAllSitesCheck", "CountFakeVotes",
            "DisableNoServiceSiteMessage", "DisableUpdateChecking", "UseVoteGUIMainCommand",
            "CloseInventoryOnVote", "ExtraVoteShopCheck");
    private static final Set<String> KNOWN_KEY_SET = Set.copyOf(KNOWN_KEYS);

    private GeneralSettingsDocument() {
    }

    /** Returns the curated fixed settings only; it never exposes document content. */
    public static Map<String, Field> fields(String redactedContent) {
        Parsed parsed = parse(redactedContent);
        LinkedHashMap<String, Field> result = new LinkedHashMap<>();
        for (String key : KNOWN_KEYS) {
            Location location = parsed.locations().get(key);
            result.put(key, location == null ? new Field(Status.MISSING, null)
                    : new Field(location.status(), location.value()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Applies explicitly chosen curated settings. A missing, non-boolean, anchored,
     * or aliased setting is never invented or rewritten.
     */
    public static String patch(String content, Map<String, Boolean> overrides) {
        validateOverrides(overrides);
        Parsed parsed = parse(content);
        List<Replacement> replacements = new ArrayList<>();
        for (Map.Entry<String, Boolean> override : overrides.entrySet()) {
            Location location = parsed.locations().get(override.getKey());
            if (location == null || location.status() != Status.AVAILABLE) throw invalidOverrides();
            if (location.value().booleanValue() != override.getValue().booleanValue()) {
                replacements.add(new Replacement(location.startOffset(), location.endOffset(),
                        Boolean.toString(override.getValue())));
            }
        }
        if (replacements.isEmpty()) return content;
        replacements.sort((left, right) -> Integer.compare(right.startOffset(), left.startOffset()));
        StringBuilder patched = new StringBuilder(content);
        int previousStart = content.length();
        for (Replacement replacement : replacements) {
            if (replacement.endOffset() > previousStart || replacement.startOffset() > replacement.endOffset()) {
                throw invalidDocument();
            }
            patched.replace(replacement.startOffset(), replacement.endOffset(), replacement.value());
            previousStart = replacement.startOffset();
        }
        return patched.toString();
    }

    private static Parsed parse(String content) {
        if (content == null || content.indexOf('\0') >= 0
                || content.codePointCount(0, content.length()) > MAX_DOCUMENT_CODE_POINTS) {
            throw invalidDocument();
        }
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(MAX_COLLECTION_ALIASES);
            options.setNestingDepthLimit(MAX_NESTING_DEPTH);
            options.setCodePointLimit(MAX_DOCUMENT_CODE_POINTS);
            options.setMergeOnCompose(false);
            Yaml yaml = new Yaml(new SafeConstructor(options));
            List<Node> documents = new ArrayList<>();
            for (Node node : yaml.composeAll(new StringReader(content))) documents.add(node);
            if (documents.size() != 1 || !(documents.get(0) instanceof MappingNode root)) throw invalidDocument();
            return new Parsed(locations(content, root));
        } catch (RuntimeException failure) {
            throw invalidDocument();
        }
    }

    private static Map<String, Location> locations(String content, MappingNode root) {
        LinkedHashMap<String, Location> result = new LinkedHashMap<>();
        Set<String> rootKeys = new java.util.HashSet<>();
        for (NodeTuple tuple : root.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) throw invalidDocument();
            String name = key.getValue();
            if (!rootKeys.add(name)) throw invalidDocument();
            if (!KNOWN_KEY_SET.contains(name)) continue;
            result.put(name, location(content, tuple.getValueNode()));
        }
        return result;
    }

    private static Location location(String content, Node value) {
        if (!(value instanceof ScalarNode scalar) || scalar.getAnchor() != null || !Tag.BOOL.equals(scalar.getTag())) {
            return new Location(Status.UNSUPPORTED, null, -1, -1);
        }
        Boolean booleanValue = yamlBoolean(scalar.getValue());
        if (booleanValue == null) return new Location(Status.UNSUPPORTED, null, -1, -1);
        int startCodePoint = scalar.getStartMark().getIndex();
        int endCodePoint = scalar.getEndMark().getIndex();
        int totalCodePoints = content.codePointCount(0, content.length());
        if (startCodePoint < 0 || endCodePoint < startCodePoint || endCodePoint > totalCodePoints) {
            throw invalidDocument();
        }
        int startOffset = content.offsetByCodePoints(0, startCodePoint);
        int endOffset = content.offsetByCodePoints(0, endCodePoint);
        return new Location(Status.AVAILABLE, booleanValue, startOffset, endOffset);
    }

    private static Boolean yamlBoolean(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on" -> Boolean.TRUE;
            case "false", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static void validateOverrides(Map<String, Boolean> overrides) {
        if (overrides == null || overrides.isEmpty() || overrides.size() > KNOWN_KEYS.size()) {
            throw invalidOverrides();
        }
        for (Map.Entry<String, Boolean> override : overrides.entrySet()) {
            if (override.getKey() == null || override.getValue() == null
                    || !KNOWN_KEY_SET.contains(override.getKey())) throw invalidOverrides();
        }
    }

    private static IllegalArgumentException invalidDocument() {
        return new IllegalArgumentException("general settings document is invalid");
    }

    private static IllegalArgumentException invalidOverrides() {
        return new IllegalArgumentException("general settings overrides are invalid");
    }

    public enum Status {
        AVAILABLE, MISSING, UNSUPPORTED
    }

    public record Field(Status status, Boolean value) {
        public Field {
            if (status == null || (status == Status.AVAILABLE) != (value != null)) {
                throw new IllegalArgumentException("field is invalid");
            }
        }
    }

    private record Parsed(Map<String, Location> locations) {
    }

    private record Location(Status status, Boolean value, int startOffset, int endOffset) {
    }

    private record Replacement(int startOffset, int endOffset, String value) {
    }
}
