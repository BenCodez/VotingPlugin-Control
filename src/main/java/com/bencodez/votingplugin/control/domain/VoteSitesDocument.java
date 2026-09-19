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
import java.util.regex.Pattern;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * A deliberately small, source-span-preserving view of the current main
 * {@code VoteSites.yml}. It is not a general YAML editor: unknown keys (and
 * Rewards in particular) are neither returned nor dumped back through YAML.
 */
public final class VoteSitesDocument {
    private static final int MAX_DOCUMENT_CODE_POINTS = 512 * 1024;
    private static final int MAX_NESTING_DEPTH = 40;
    private static final int MAX_COLLECTION_ALIASES = 16;
    private static final int MAX_SITES = 200;
    private static final int MAX_SITE_KEY_CODE_POINTS = 256;
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern MATERIAL = Pattern.compile("[A-Za-z0-9_:-]{1,100}");
    private static final List<String> FIELDS = List.of("Enabled", "Name", "ServiceSite", "VoteURL", "VoteDelay",
            "Priority", "Hidden", "DisplayItem.Material", "DisplayItem.Amount");
    private static final Set<String> FIELD_SET = Set.copyOf(FIELDS);

    private VoteSitesDocument() {
    }

    /** Returns only bounded site metadata and the nine allow-listed fields. */
    public static Inventory inventory(String content) {
        Parsed parsed = parse(content);
        List<Site> sites = new ArrayList<>();
        for (SiteInfo site : parsed.sites()) {
            LinkedHashMap<String, Field> fields = new LinkedHashMap<>();
            for (String name : FIELDS) fields.put(name, readField(site, name));
            sites.add(new Site(site.key(), safeKey(site.key()), Collections.unmodifiableMap(fields), site.rewardsConfigured()));
        }
        return new Inventory(Collections.unmodifiableList(sites));
    }

    /** Adds one safe, absent site with all nine typed fields. */
    public static String add(String content, String siteKey, Map<String, ?> fields) {
        Parsed parsed = parse(content);
        if (!safeKey(siteKey) || parsed.sites().stream().anyMatch(site -> site.key().equalsIgnoreCase(siteKey))
                || parsed.sites().size() >= MAX_SITES) {
            throw invalidOperation();
        }
        Map<String, Object> checked = validateFields(fields, true);
        int indent = parsed.siteIndent();
        int at = blockEnd(content, afterLine(content, parsed.voteSitesKeyStart()), parsed.voteSitesIndent());
        String insertion = insertionPrefix(content, at) + spaces(indent) + yamlQuote(siteKey) + ":\n"
                + renderFields(checked, indent + 2);
        return content.substring(0, at) + insertion + content.substring(at);
    }

    /** Applies one or more typed allow-listed fields to exactly one safe site. */
    public static String edit(String content, String siteKey, Map<String, ?> overrides) {
        Parsed parsed = parse(content);
        SiteInfo site = parsed.byKey().get(siteKey);
        if (!safeKey(siteKey) || site == null) throw invalidOperation();
        Map<String, Object> checked = validateFields(overrides, false);
        List<Replacement> replacements = new ArrayList<>();
        LinkedHashMap<String, Object> missingDirect = new LinkedHashMap<>();
        LinkedHashMap<String, Object> missingDisplay = new LinkedHashMap<>();
        for (Map.Entry<String, Object> override : checked.entrySet()) {
            FieldLocation location = fieldLocation(content, site, override.getKey());
            if (location == null) {
                (override.getKey().startsWith("DisplayItem.") ? missingDisplay : missingDirect)
                        .put(override.getKey(), override.getValue());
            } else {
                if (location.status() != Status.AVAILABLE) throw invalidOperation();
                String value = renderValue(override.getKey(), override.getValue());
                if (!location.value().equals(override.getValue())) {
                    replacements.add(new Replacement(location.startOffset(), location.endOffset(), value));
                }
            }
        }
        if (!missingDirect.isEmpty()) {
            int at = blockEnd(content, afterLine(content, site.keyStartOffset()), site.keyIndent());
            String inserted = insertionPrefix(content, at) + renderProperties(missingDirect, site.propertyIndent());
            if (!missingDisplay.isEmpty() && site.properties().get("DisplayItem") == null) {
                inserted += spaces(site.propertyIndent()) + "DisplayItem:\n"
                        + renderDisplayProperties(missingDisplay, site.propertyIndent() + 2);
                missingDisplay.clear();
            }
            replacements.add(new Replacement(at, at, inserted));
        }
        if (!missingDisplay.isEmpty()) {
            NodeTuple displayTuple = site.properties().get("DisplayItem");
            if (displayTuple == null) {
                int at = blockEnd(content, afterLine(content, site.keyStartOffset()), site.keyIndent());
                replacements.add(new Replacement(at, at, insertionPrefix(content, at) + spaces(site.propertyIndent())
                        + "DisplayItem:\n" + renderDisplayProperties(missingDisplay, site.propertyIndent() + 2)));
            } else if (displayTuple.getValueNode() instanceof MappingNode display
                    && isBlock(display) && display.getAnchor() == null) {
                int keyStart = offset(content, displayTuple.getKeyNode().getStartMark().getIndex());
                int at = blockEnd(content, afterLine(content, keyStart), site.propertyIndent());
                replacements.add(new Replacement(at, at,
                        insertionPrefix(content, at) + renderDisplayProperties(missingDisplay,
                                childIndent(display, site.propertyIndent(), site.propertyIndent() + 2, content))));
            } else {
                throw invalidOperation();
            }
        }
        return apply(content, replacements);
    }

    /** Removes exactly one safe site mapping, retaining trailing comments outside its block. */
    public static String remove(String content, String siteKey) {
        Parsed parsed = parse(content);
        SiteInfo site = parsed.byKey().get(siteKey);
        if (!safeKey(siteKey) || site == null) throw invalidOperation();
        int start = lineStart(content, site.keyStartOffset());
        int boundary = blockEnd(content, afterLine(content, site.keyStartOffset()), site.keyIndent());
        int end = meaningfulBlockEnd(content, start, boundary);
        return content.substring(0, start) + content.substring(end);
    }

    private static Parsed parse(String content) {
        if (content == null || content.indexOf('\0') >= 0
                || content.codePointCount(0, content.length()) > MAX_DOCUMENT_CODE_POINTS) throw invalidDocument();
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(MAX_COLLECTION_ALIASES);
            options.setNestingDepthLimit(MAX_NESTING_DEPTH);
            options.setCodePointLimit(MAX_DOCUMENT_CODE_POINTS);
            options.setMergeOnCompose(false);
            List<Node> documents = new ArrayList<>();
            for (Node node : new Yaml(new SafeConstructor(options)).composeAll(new StringReader(content))) documents.add(node);
            if (documents.size() != 1 || !(documents.get(0) instanceof MappingNode root) || !isBlock(root)) throw invalidDocument();
            MappingNode voteSites = null;
            boolean emptyVoteSites = false;
            int voteSitesIndent = -1;
            int voteSitesKeyStart = -1;
            Set<String> rootKeys = new HashSet<>();
            for (NodeTuple tuple : root.getValue()) {
                String key = scalarKey(tuple.getKeyNode());
                if (!rootKeys.add(key)) throw invalidDocument();
                if ("VoteSites".equals(key)) {
                    if (voteSites != null || emptyVoteSites) {
                        throw invalidDocument();
                    }
                    if (tuple.getValueNode() instanceof MappingNode mapping && isBlock(mapping)) {
                        voteSites = mapping;
                    } else if (tuple.getValueNode() instanceof ScalarNode scalar && Tag.NULL.equals(scalar.getTag())
                            && scalar.getValue().isEmpty()) {
                        // Removing the last site leaves "VoteSites:"; allow a later
                        // explicit add without making the document unmanageable.
                        emptyVoteSites = true;
                    } else {
                        throw invalidDocument();
                    }
                    voteSitesIndent = column(tuple.getKeyNode(), content);
                    voteSitesKeyStart = offset(content, tuple.getKeyNode().getStartMark().getIndex());
                }
            }
            if ((voteSites == null && !emptyVoteSites) || voteSites != null && voteSites.getAnchor() != null) throw invalidDocument();
            if (voteSites != null) rejectAnchorsAliasesAndMerges(voteSites);
            List<SiteInfo> sites = new ArrayList<>();
            LinkedHashMap<String, SiteInfo> byKey = new LinkedHashMap<>();
            Set<String> folded = new HashSet<>();
            int siteIndent = -1;
            for (NodeTuple tuple : voteSites == null ? List.<NodeTuple>of() : voteSites.getValue()) {
                String key = scalarKey(tuple.getKeyNode());
                if (byKey.containsKey(key) || !folded.add(key.toLowerCase(Locale.ROOT))
                        || key.codePointCount(0, key.length()) > MAX_SITE_KEY_CODE_POINTS
                        || !(tuple.getValueNode() instanceof MappingNode site) || !isBlock(site)) throw invalidDocument();
                int keyIndent = column(tuple.getKeyNode(), content);
                if (keyIndent <= voteSitesIndent) throw invalidDocument();
                if (siteIndent < 0) siteIndent = keyIndent;
                else if (siteIndent != keyIndent) throw invalidDocument();
                LinkedHashMap<String, NodeTuple> properties = properties(site);
                boolean rewardsConfigured = hasContent(properties.get("Rewards"));
                SiteInfo info = new SiteInfo(key, site, properties, keyIndent,
                        childIndent(site, keyIndent, keyIndent + 2, content),
                        offset(content, tuple.getKeyNode().getStartMark().getIndex()), rewardsConfigured);
                sites.add(info);
                byKey.put(key, info);
                if (sites.size() > MAX_SITES) throw invalidDocument();
            }
            return new Parsed(Collections.unmodifiableList(sites), Collections.unmodifiableMap(byKey), voteSitesIndent,
                    voteSitesKeyStart, siteIndent < 0 ? voteSitesIndent + 2 : siteIndent);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalidDocument();
        }
    }

    private static LinkedHashMap<String, NodeTuple> properties(MappingNode mapping) {
        LinkedHashMap<String, NodeTuple> result = new LinkedHashMap<>();
        Set<String> folded = new HashSet<>();
        for (NodeTuple tuple : mapping.getValue()) {
            String key = scalarKey(tuple.getKeyNode());
            if (!folded.add(key.toLowerCase(Locale.ROOT)) || result.put(key, tuple) != null) throw invalidDocument();
        }
        return result;
    }

    private static boolean hasNonCanonicalKey(Map<String, NodeTuple> properties, String canonical) {
        return properties.keySet().stream().anyMatch(key -> !key.equals(canonical) && key.equalsIgnoreCase(canonical));
    }

    private static Field readField(SiteInfo site, String name) {
        FieldLocation location = fieldLocation(null, site, name);
        return location == null ? new Field(Status.MISSING, null) : new Field(location.status(), location.value());
    }

    private static FieldLocation fieldLocation(String content, SiteInfo site, String name) {
        Node value;
        if (name.startsWith("DisplayItem.")) {
            NodeTuple display = site.properties().get("DisplayItem");
            if (display == null) return hasNonCanonicalKey(site.properties(), "DisplayItem")
                    ? new FieldLocation(Status.UNSUPPORTED, null, -1, -1) : null;
            if (!(display.getValueNode() instanceof MappingNode map) || !isBlock(map)) {
                return new FieldLocation(Status.UNSUPPORTED, null, -1, -1);
            }
            Map<String, NodeTuple> nested = properties(map);
            String child = name.substring("DisplayItem.".length());
            NodeTuple tuple = nested.get(child);
            if (tuple == null) return hasNonCanonicalKey(nested, child)
                    ? new FieldLocation(Status.UNSUPPORTED, null, -1, -1) : null;
            value = tuple.getValueNode();
        } else {
            NodeTuple tuple = site.properties().get(name);
            if (tuple == null) return hasNonCanonicalKey(site.properties(), name)
                    ? new FieldLocation(Status.UNSUPPORTED, null, -1, -1) : null;
            value = tuple.getValueNode();
        }
        return scalarLocation(content, value, name);
    }

    private static FieldLocation scalarLocation(String content, Node node, String name) {
        if (!(node instanceof ScalarNode scalar) || scalar.getAnchor() != null) {
            return new FieldLocation(Status.UNSUPPORTED, null, -1, -1);
        }
        Object value = switch (name) {
            case "Enabled", "Hidden" -> booleanValue(scalar);
            case "Priority", "DisplayItem.Amount" -> integerValue(scalar);
            default -> stringValue(scalar);
        };
        if (value == null) return new FieldLocation(Status.UNSUPPORTED, null, -1, -1);
        if (!validInput(name, value)) return new FieldLocation(Status.UNSUPPORTED, null, -1, -1);
        int start = scalar.getStartMark().getIndex();
        int end = scalar.getEndMark().getIndex();
        if (content != null) {
            start = offset(content, start);
            end = offset(content, end);
        }
        return new FieldLocation(Status.AVAILABLE, value, start, end);
    }

    private static Boolean booleanValue(ScalarNode scalar) {
        if (!Tag.BOOL.equals(scalar.getTag())) return null;
        return switch (scalar.getValue().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> Boolean.TRUE;
            case "false", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Integer integerValue(ScalarNode scalar) {
        if (!Tag.INT.equals(scalar.getTag()) || !scalar.getValue().matches("[-+]?(0|[1-9][0-9]*)")) return null;
        try {
            return Integer.valueOf(scalar.getValue());
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    private static String stringValue(ScalarNode scalar) {
        if (!Tag.STR.equals(scalar.getTag()) || scalar.getScalarStyle() == DumperOptions.ScalarStyle.LITERAL
                || scalar.getScalarStyle() == DumperOptions.ScalarStyle.FOLDED) return null;
        return scalar.getValue();
    }

    private static Map<String, Object> validateFields(Map<String, ?> fields, boolean complete) {
        if (fields == null || fields.isEmpty() || fields.size() > FIELDS.size() || (complete && fields.size() != FIELDS.size())) {
            throw invalidOperation();
        }
        LinkedHashMap<String, Object> checked = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (name == null || !FIELD_SET.contains(name) || checked.put(name, value) != null || !validInput(name, value)) {
                throw invalidOperation();
            }
        }
        if (complete && !checked.keySet().containsAll(FIELD_SET)) throw invalidOperation();
        return checked;
    }

    private static boolean validInput(String name, Object value) {
        if ("Enabled".equals(name) || "Hidden".equals(name)) return value instanceof Boolean;
        if ("Priority".equals(name)) return value instanceof Integer;
        if ("DisplayItem.Amount".equals(name)) return value instanceof Integer integer && integer >= 1 && integer <= 64;
        if (!(value instanceof String text) || text.indexOf('\0') >= 0) return false;
        return switch (name) {
            case "Name" -> text.codePointCount(0, text.length()) <= 200;
            case "ServiceSite" -> visibleSingleLine(text, 1, 2048);
            case "VoteURL" -> visibleSingleLine(text, 1, 500);
            case "VoteDelay" -> visibleSingleLine(text, 1, 64);
            case "DisplayItem.Material" -> MATERIAL.matcher(text).matches();
            default -> false;
        };
    }

    private static boolean visibleSingleLine(String value, int min, int max) {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('\t') >= 0 || value.indexOf('|') >= 0) return false;
        for (int i = 0; i < value.length();) {
            int point = value.codePointAt(i);
            if (Character.isISOControl(point)) return false;
            i += Character.charCount(point);
        }
        return true;
    }

    private static String renderFields(Map<String, Object> fields, int indent) {
        LinkedHashMap<String, Object> direct = new LinkedHashMap<>();
        LinkedHashMap<String, Object> display = new LinkedHashMap<>();
        for (String field : FIELDS) {
            if (field.startsWith("DisplayItem.")) display.put(field, fields.get(field));
            else direct.put(field, fields.get(field));
        }
        return renderProperties(direct, indent) + spaces(indent) + "DisplayItem:\n" + renderDisplayProperties(display, indent + 2);
    }

    private static String renderProperties(Map<String, Object> fields, int indent) {
        StringBuilder result = new StringBuilder();
        for (String field : FIELDS) {
            if (field.startsWith("DisplayItem.") || !fields.containsKey(field)) continue;
            result.append(spaces(indent)).append(field).append(": ").append(renderValue(field, fields.get(field))).append('\n');
        }
        return result.toString();
    }

    private static String renderDisplayProperties(Map<String, Object> fields, int indent) {
        StringBuilder result = new StringBuilder();
        for (String field : List.of("DisplayItem.Material", "DisplayItem.Amount")) {
            if (fields.containsKey(field)) result.append(spaces(indent)).append(field.substring("DisplayItem.".length()))
                    .append(": ").append(renderValue(field, fields.get(field))).append('\n');
        }
        return result.toString();
    }

    private static String renderValue(String field, Object value) {
        return value instanceof String text ? yamlQuote(text) : String.valueOf(value);
    }

    private static String yamlQuote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length();) {
            int point = value.codePointAt(i);
            switch (point) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (Character.isISOControl(point)) result.append(String.format("\\u%04X", point));
                    else result.appendCodePoint(point);
                }
            }
            i += Character.charCount(point);
        }
        return result.append('"').toString();
    }

    private static String apply(String content, List<Replacement> replacements) {
        if (replacements.isEmpty()) return content;
        replacements.sort((left, right) -> Integer.compare(right.startOffset(), left.startOffset()));
        StringBuilder result = new StringBuilder(content);
        int previous = content.length();
        for (Replacement replacement : replacements) {
            if (replacement.startOffset() < 0 || replacement.startOffset() > replacement.endOffset()
                    || replacement.endOffset() > previous) throw invalidOperation();
            result.replace(replacement.startOffset(), replacement.endOffset(), replacement.value());
            previous = replacement.startOffset();
        }
        return result.toString();
    }

    private static void rejectAnchorsAliasesAndMerges(Node node) {
        if (node.getAnchor() != null || node instanceof AnchorNode) throw invalidDocument();
        if (node instanceof MappingNode mapping) {
            if (mapping.isMerged()) throw invalidDocument();
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode scalar && "<<".equals(scalar.getValue())) throw invalidDocument();
                rejectAnchorsAliasesAndMerges(tuple.getKeyNode());
                rejectAnchorsAliasesAndMerges(tuple.getValueNode());
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node item : sequence.getValue()) rejectAnchorsAliasesAndMerges(item);
        }
    }

    private static String scalarKey(Node node) {
        if (!(node instanceof ScalarNode scalar) || scalar.getAnchor() != null) throw invalidDocument();
        return scalar.getValue();
    }

    private static boolean hasContent(NodeTuple tuple) {
        if (tuple == null) return false;
        Node node = tuple.getValueNode();
        if (node instanceof MappingNode mapping) return !mapping.getValue().isEmpty();
        if (node instanceof SequenceNode sequence) return !sequence.getValue().isEmpty();
        return node instanceof ScalarNode scalar && !scalar.getValue().trim().isEmpty() && !Tag.NULL.equals(scalar.getTag());
    }

    private static boolean isBlock(MappingNode node) {
        return DumperOptions.FlowStyle.BLOCK.equals(node.getFlowStyle());
    }

    private static boolean safeKey(String key) {
        return key != null && SAFE_KEY.matcher(key).matches();
    }

    private static int column(Node node, String content) {
        int column = node.getStartMark().getColumn();
        if (column < 0 || column > content.length()) throw invalidDocument();
        return column;
    }

    private static int childIndent(MappingNode mapping, int parentIndent, int fallback, String content) {
        if (mapping.getValue().isEmpty()) return fallback;
        int indent = column(mapping.getValue().get(0).getKeyNode(), content);
        if (indent <= parentIndent) throw invalidDocument();
        return indent;
    }

    private static int offset(String content, int codePoints) {
        int count = content.codePointCount(0, content.length());
        if (codePoints < 0 || codePoints > count) throw invalidDocument();
        return content.offsetByCodePoints(0, codePoints);
    }

    private static int blockEnd(String content, int start, int parentIndent) {
        int offset = start;
        int result = content.length();
        while (offset < content.length()) {
            int next = content.indexOf('\n', offset);
            int end = next < 0 ? content.length() : next + 1;
            int first = offset;
            while (first < end && content.charAt(first) == ' ') first++;
            if (first < end && content.charAt(first) == '\t') throw invalidOperation();
            if (first < end && content.charAt(first) != '\n' && content.charAt(first) != '\r' && content.charAt(first) != '#') {
                int indent = first - offset;
                if (indent <= parentIndent) return offset;
            }
            offset = end;
        }
        return result;
    }

    private static int meaningfulBlockEnd(String content, int start, int boundary) {
        int cursor = start;
        int last = start;
        while (cursor < boundary) {
            int next = content.indexOf('\n', cursor);
            int end = next < 0 || next >= boundary ? boundary : next + 1;
            int first = cursor;
            while (first < end && content.charAt(first) == ' ') first++;
            if (first < end && content.charAt(first) != '#' && content.charAt(first) != '\n' && content.charAt(first) != '\r') {
                last = end;
            }
            cursor = end;
        }
        return last;
    }

    private static int lineStart(String content, int offset) {
        int index = Math.min(offset, content.length());
        while (index > 0 && content.charAt(index - 1) != '\n') index--;
        return index;
    }

    private static int afterLine(String content, int offset) {
        int newline = content.indexOf('\n', lineStart(content, offset));
        return newline < 0 ? content.length() : newline + 1;
    }

    private static String insertionPrefix(String content, int at) {
        return at > 0 && content.charAt(at - 1) != '\n' ? "\n" : "";
    }

    private static String spaces(int count) {
        return " ".repeat(count);
    }

    private static IllegalArgumentException invalidDocument() {
        return new IllegalArgumentException("vote sites document is invalid");
    }

    private static IllegalArgumentException invalidOperation() {
        return new IllegalArgumentException("vote sites operation is invalid");
    }

    public enum Status { AVAILABLE, MISSING, UNSUPPORTED }

    public record Field(Status status, Object value) {
        public Field {
            if (status == null || (status == Status.AVAILABLE) != (value != null)) throw new IllegalArgumentException("field is invalid");
        }
    }

    public record Site(String key, boolean editable, Map<String, Field> fields, boolean rewardsConfigured) {
        public Site {
            if (key == null || fields == null) throw new IllegalArgumentException("site is invalid");
        }
    }

    public record Inventory(List<Site> sites) {
        public Inventory {
            if (sites == null || sites.size() > MAX_SITES) throw new IllegalArgumentException("inventory is invalid");
        }
    }

    private record Parsed(List<SiteInfo> sites, Map<String, SiteInfo> byKey, int voteSitesIndent,
            int voteSitesKeyStart, int siteIndent) { }
    private record SiteInfo(String key, MappingNode node, Map<String, NodeTuple> properties, int keyIndent,
            int propertyIndent, int keyStartOffset, boolean rewardsConfigured) { }
    private record FieldLocation(Status status, Object value, int startOffset, int endOffset) { }
    private record Replacement(int startOffset, int endOffset, String value) { }
}
