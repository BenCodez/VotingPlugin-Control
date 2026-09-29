package com.bencodez.votingplugin.control.domain;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * Bounded, lossless access to the curated backend and proxy General Settings.
 * Scalar source spans are replaced directly so unrelated YAML, comments,
 * ordering, redactions, and unknown settings remain unchanged.
 */
public final class GeneralSettingsDocument {
    private static final int MAX_DOCUMENT_CODE_POINTS = 512 * 1024;
    private static final int MAX_NESTING_DEPTH = 40;
    private static final int MAX_COLLECTION_ALIASES = 16;
    private static final int MAX_PREFIX_CODE_POINTS = 32;

    private static final List<Spec> BACKEND_SPECS = List.of(
            enumSpec("Debug", "DebugLevel", Set.of("NONE", "INFO", "EXTRA")),
            booleanSpec("OnlineMode", "OnlineMode"),
            booleanSpec("AutoCreateVoteSites", "AutoCreateVoteSites"),
            booleanSpec("UseVoteGUIMainCommand", "UseVoteGUIMainCommand"),
            booleanSpec("CountFakeVotes", "CountFakeVotes"),
            booleanSpec("AllowUnjoined", "AllowUnjoined"),
            booleanSpec("GiveDefaultPermission", "GiveDefaultPermission"),
            booleanSpec("LoadCommandAliases", "LoadCommandAliases"),
            booleanSpec("CaseInsensitiveYMLFiles", "CaseInsensitiveYMLFiles"),
            stringSpec("BedrockPlayerPrefix", "BedrockPlayerPrefix"),
            booleanSpec("PerSiteCoolDownEvents", "PerSiteCoolDownEvents"),
            invertedBooleanSpec("CheckForUpdates", "DisableUpdateChecking"),
            booleanSpec("CloseInventoryOnVote", "CloseInventoryOnVote"));
    private static final List<Spec> PROXY_SPECS = List.of(
            proxyDebugSpec(),
            booleanSpec("OnlineMode", "OnlineMode"),
            booleanSpec("AllowUnjoined", "AllowUnJoined"),
            stringSpec("BedrockPlayerPrefix", "BedrockPlayerPrefix"));

    private GeneralSettingsDocument() {
    }

    /** Existing callers default to the Bukkit Config.yml profile. */
    public static Map<String, Field> fields(String redactedContent) {
        return fields(redactedContent, Profile.BACKEND);
    }

    public static Map<String, Field> fields(String redactedContent, Profile profile) {
        List<Spec> specs = specs(profile);
        Parsed parsed = parse(redactedContent, specs);
        LinkedHashMap<String, Field> result = new LinkedHashMap<>();
        for (Spec spec : specs) {
            Location location = parsed.locations().get(spec.key());
            result.put(spec.key(), location == null ? new Field(Status.MISSING, null)
                    : new Field(location.status(), location.value()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Existing callers default to the Bukkit Config.yml profile. */
    public static String patch(String content, Map<String, ?> overrides) {
        return patch(content, Profile.BACKEND, overrides);
    }

    public static String patch(String content, Profile profile, Map<String, ?> overrides) {
        List<Spec> specs = specs(profile);
        Map<String, Spec> byKey = byKey(specs);
        validateOverrides(overrides, byKey);
        Parsed parsed = parse(content, specs);
        List<Replacement> replacements = new ArrayList<>();
        Set<String> rawKeys = new HashSet<>();
        for (Map.Entry<String, ?> override : overrides.entrySet()) {
            Spec spec = byKey.get(override.getKey());
            if (!rawKeys.add(spec.rawKey())) throw invalidOverrides();
            Location location = parsed.locations().get(override.getKey());
            if (location == null || location.status() != Status.AVAILABLE) throw invalidOverrides();
            Object requested = spec.normalize(override.getValue());
            if (!requested.equals(location.value())) {
                replacements.add(new Replacement(location.startOffset(), location.endOffset(), spec.encode(requested)));
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

    public static Profile profileForFile(String fileName) {
        if ("Config.yml".equals(fileName)) return Profile.BACKEND;
        if ("bungeeconfig.yml".equals(fileName)) return Profile.PROXY;
        throw invalidDocument();
    }

    public static String fileName(Profile profile) {
        return switch (requireProfile(profile)) {
            case BACKEND -> "Config.yml";
            case PROXY -> "bungeeconfig.yml";
        };
    }

    public static int maxOverrides(Profile profile) {
        return specs(profile).size();
    }

    private static Parsed parse(String content, List<Spec> specs) {
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
            return new Parsed(locations(content, root, specs));
        } catch (RuntimeException failure) {
            throw invalidDocument();
        }
    }

    private static Map<String, Location> locations(String content, MappingNode root, List<Spec> specs) {
        LinkedHashMap<String, Location> result = new LinkedHashMap<>();
        Map<String, List<Spec>> byRawKey = new LinkedHashMap<>();
        for (Spec spec : specs) byRawKey.computeIfAbsent(spec.rawKey(), ignored -> new ArrayList<>()).add(spec);
        Set<String> rootKeys = new HashSet<>();
        Set<String> foldedRootKeys = new HashSet<>();
        for (NodeTuple tuple : root.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) throw invalidDocument();
            String name = key.getValue();
            if (!rootKeys.add(name) || !foldedRootKeys.add(name.toLowerCase(Locale.ROOT))) throw invalidDocument();
            List<Spec> matching = byRawKey.get(name);
            if (matching == null) continue;
            for (Spec spec : matching) result.put(spec.key(), location(content, tuple.getValueNode(), spec));
        }
        return result;
    }

    private static Location location(String content, Node value, Spec spec) {
        if (!(value instanceof ScalarNode scalar) || scalar.getAnchor() != null) return unsupported();
        Object decoded = spec.decode(scalar);
        if (decoded == null) return unsupported();
        int startCodePoint = scalar.getStartMark().getIndex();
        int endCodePoint = scalar.getEndMark().getIndex();
        int totalCodePoints = content.codePointCount(0, content.length());
        if (startCodePoint < 0 || endCodePoint < startCodePoint || endCodePoint > totalCodePoints) {
            throw invalidDocument();
        }
        return new Location(Status.AVAILABLE, decoded,
                content.offsetByCodePoints(0, startCodePoint), content.offsetByCodePoints(0, endCodePoint));
    }

    private static Location unsupported() {
        return new Location(Status.UNSUPPORTED, null, -1, -1);
    }

    private static Boolean yamlBoolean(ScalarNode scalar) {
        if (!Tag.BOOL.equals(scalar.getTag())) return null;
        return switch (scalar.getValue().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> Boolean.TRUE;
            case "false", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static String yamlString(ScalarNode scalar) {
        if (!Tag.STR.equals(scalar.getTag())) return null;
        String value = scalar.getValue();
        if (value.codePointCount(0, value.length()) > MAX_PREFIX_CODE_POINTS
                || value.chars().anyMatch(character -> Character.isISOControl(character))) return null;
        return value;
    }

    private static void validateOverrides(Map<String, ?> overrides, Map<String, Spec> byKey) {
        if (overrides == null || overrides.isEmpty() || overrides.size() > byKey.size()) throw invalidOverrides();
        for (Map.Entry<String, ?> override : overrides.entrySet()) {
            Spec spec = byKey.get(override.getKey());
            if (spec == null || override.getValue() == null) throw invalidOverrides();
            spec.normalize(override.getValue());
        }
    }

    private static List<Spec> specs(Profile profile) {
        return switch (requireProfile(profile)) {
            case BACKEND -> BACKEND_SPECS;
            case PROXY -> PROXY_SPECS;
        };
    }

    private static Profile requireProfile(Profile profile) {
        if (profile == null) throw invalidDocument();
        return profile;
    }

    private static Map<String, Spec> byKey(List<Spec> specs) {
        LinkedHashMap<String, Spec> result = new LinkedHashMap<>();
        for (Spec spec : specs) result.put(spec.key(), spec);
        return result;
    }

    private static Spec booleanSpec(String key, String rawKey) {
        return new Spec(key, rawKey, Kind.BOOLEAN, Set.of(), false);
    }

    private static Spec invertedBooleanSpec(String key, String rawKey) {
        return new Spec(key, rawKey, Kind.BOOLEAN, Set.of(), true);
    }

    private static Spec enumSpec(String key, String rawKey, Set<String> values) {
        return new Spec(key, rawKey, Kind.ENUM, values, false);
    }

    private static Spec stringSpec(String key, String rawKey) {
        return new Spec(key, rawKey, Kind.STRING, Set.of(), false);
    }

    private static Spec proxyDebugSpec() {
        return new Spec("Debug", "Debug", Kind.PROXY_DEBUG, Set.of("NONE", "INFO", "EXTRA"), false);
    }

    private static IllegalArgumentException invalidDocument() {
        return new IllegalArgumentException("general settings document is invalid");
    }

    private static IllegalArgumentException invalidOverrides() {
        return new IllegalArgumentException("general settings overrides are invalid");
    }

    public enum Profile { BACKEND, PROXY }
    public enum Status { AVAILABLE, MISSING, UNSUPPORTED }

    public record Field(Status status, Object value) {
        public Field {
            if (status == null || (status == Status.AVAILABLE) != (value != null)
                    || value != null && !(value instanceof Boolean) && !(value instanceof String)) {
                throw new IllegalArgumentException("field is invalid");
            }
        }
    }

    private enum Kind { BOOLEAN, ENUM, STRING, PROXY_DEBUG }

    private record Spec(String key, String rawKey, Kind kind, Set<String> values, boolean invert) {
        Object decode(ScalarNode scalar) {
            return switch (kind) {
                case BOOLEAN -> {
                    Boolean value = yamlBoolean(scalar);
                    yield value == null ? null : invert ? !value : value;
                }
                case ENUM -> {
                    if (!Tag.STR.equals(scalar.getTag())) yield null;
                    String value = scalar.getValue().toUpperCase(Locale.ROOT);
                    yield values.contains(value) ? value : null;
                }
                case STRING -> yamlString(scalar);
                case PROXY_DEBUG -> {
                    Boolean value = yamlBoolean(scalar);
                    yield value == null ? null : value ? "INFO" : "NONE";
                }
            };
        }

        Object normalize(Object value) {
            return switch (kind) {
                case BOOLEAN -> {
                    if (!(value instanceof Boolean booleanValue)) throw invalidOverrides();
                    yield booleanValue;
                }
                case ENUM, PROXY_DEBUG -> {
                    if (!(value instanceof String stringValue)) throw invalidOverrides();
                    String normalized = stringValue.toUpperCase(Locale.ROOT);
                    if (!values.contains(normalized)) throw invalidOverrides();
                    yield kind == Kind.PROXY_DEBUG && "EXTRA".equals(normalized) ? "INFO" : normalized;
                }
                case STRING -> {
                    if (!(value instanceof String stringValue)
                            || stringValue.codePointCount(0, stringValue.length()) > MAX_PREFIX_CODE_POINTS
                            || stringValue.chars().anyMatch(character -> Character.isISOControl(character))) {
                        throw invalidOverrides();
                    }
                    yield stringValue;
                }
            };
        }

        String encode(Object normalized) {
            return switch (kind) {
                case BOOLEAN -> Boolean.toString(invert ? !(Boolean) normalized : (Boolean) normalized);
                case ENUM -> (String) normalized;
                case STRING -> "'" + ((String) normalized).replace("'", "''") + "'";
                case PROXY_DEBUG -> Boolean.toString(!"NONE".equals(normalized));
            };
        }
    }

    private record Parsed(Map<String, Location> locations) { }
    private record Location(Status status, Object value, int startOffset, int endOffset) { }
    private record Replacement(int startOffset, int endOffset, String value) { }
}
