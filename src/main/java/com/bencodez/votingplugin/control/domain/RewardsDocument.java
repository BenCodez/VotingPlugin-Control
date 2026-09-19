package com.bencodez.votingplugin.control.domain;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

/** Bounded, source-span based reward inspection and editing; never serializes a reward subtree. */
public final class RewardsDocument {
    private static final int MAX_SOURCE = 512 * 1024;
    private static final int MAX_REWARDS = 200;
    private static final int MAX_LIST = 100;
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern ITEM_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern MATERIAL = Pattern.compile("[A-Z0-9_]{1,80}");
    private static final Set<String> EDITABLE_FIELDS = Set.of("Commands", "Commands.Console", "Commands.Player",
            "Messages.Player", "Messages.Broadcast", "Money", "Chance");

    private RewardsDocument() { }

    public static List<Scope> inventory(String content, String fileName) {
        MappingNode root = parse(content);
        List<Scope> result = new ArrayList<>();
        if (namedRewardFile(fileName)) {
            addScope(result, "$", root, true);
        } else if ("VoteSites.yml".equals(fileName)) {
            Node sites = child(root, "VoteSites");
            if (sites instanceof MappingNode mapping) {
                for (NodeTuple tuple : mapping.getValue()) {
                    String key = key(tuple.getKeyNode());
                    if (!(tuple.getValueNode() instanceof MappingNode site)) continue;
                    addScope(result, "VoteSites." + key + ".Rewards", child(site, "Rewards"), SITE_KEY.matcher(key).matches());
                    for (String extra : List.of("WaitUntilVoteDelayRewards", "CoolDownEndRewards")) {
                        if (child(site, extra) != null) addScope(result, "VoteSites." + key + "." + extra, child(site, extra), false);
                    }
                }
            }
            addScope(result, "EverySiteReward", child(root, "EverySiteReward"), false);
        } else if ("SpecialRewards.yml".equals(fileName) || "Config.yml".equals(fileName)) {
            walkRewards(result, root, "", 0);
            if ("SpecialRewards.yml".equals(fileName) && child(root, "AnySiteRewards") == null)
                addScope(result, "AnySiteRewards", null, false);
        } else throw invalid();
        return List.copyOf(result);
    }

    /** One explicitly selected operation against one retained target's own document. */
    public static String patch(String content, String fileName, String rewardPath, Edit edit) {
        if (edit == null || edit.operation() == null || rewardPath == null) throw invalid();
        MappingNode root = parse(content);
        if (namedRewardFile(fileName)) {
            if (!"$".equals(rewardPath) || "CREATE_REWARD".equals(edit.operation())
                    || "REMOVE_REWARD".equals(edit.operation()) || !block(root)) throw invalid();
            return patchFields(content, root, null, edit);
        }
        if (!"VoteSites.yml".equals(fileName)) throw invalid();
        String[] parts = rewardPath.split("\\.", -1);
        if (parts.length != 3 || !"VoteSites".equals(parts[0]) || !"Rewards".equals(parts[2])
                || !SITE_KEY.matcher(parts[1]).matches()) throw invalid();
        Node sites = child(root, "VoteSites");
        Node site = sites instanceof MappingNode mapping ? child(mapping, parts[1]) : null;
        if (!(site instanceof MappingNode siteMap) || !block(siteMap)) throw invalid();
        NodeTuple rewardTuple = tuple(siteMap, "Rewards");
        if ("CREATE_REWARD".equals(edit.operation())) {
            if (rewardTuple != null || !"Commands".equals(edit.field()) || !(edit.value() instanceof String command)) throw invalid();
            validateText(command);
            int at = blockEnd(content, afterLine(content, offset(content, siteMap.getStartMark().getIndex())), siteMap.getStartMark().getColumn());
            int indent = childIndent(siteMap, siteMap.getStartMark().getColumn() + 2);
            return insert(content, at, spaces(indent) + "Rewards:\n" + spaces(indent + 2) + "Commands:\n"
                    + spaces(indent + 2) + "- " + quote(command) + "\n");
        }
        if (rewardTuple == null || !(rewardTuple.getValueNode() instanceof MappingNode reward) || !block(reward)) throw invalid();
        if ("REMOVE_REWARD".equals(edit.operation())) {
            if (edit.field() != null || edit.value() != null) throw invalid();
            int start = lineStart(content, offset(content, rewardTuple.getKeyNode().getStartMark().getIndex()));
            int end = blockEnd(content, afterLine(content, start), rewardTuple.getKeyNode().getStartMark().getColumn());
            return content.substring(0, start) + content.substring(end);
        }
        return patchFields(content, reward, rewardTuple, edit);
    }

    private static String patchFields(String content, MappingNode reward, NodeTuple rewardTuple, Edit edit) {
        if (edit.field() == null || (!EDITABLE_FIELDS.contains(edit.field()) && !itemField(edit.field()))) throw invalid();
        if (Set.of("APPEND_LIST_ENTRY", "REMOVE_LIST_ENTRY", "REPLACE_LIST").contains(edit.operation())
                && !"Commands".equals(edit.field())) throw invalid();
        NodeTuple field = findTuple(reward, edit.field());
        if (field == null) {
            if (!"SET_SCALAR".equals(edit.operation()) && !"APPEND_LIST_ENTRY".equals(edit.operation())) throw invalid();
            return addMissingField(content, reward, rewardTuple, edit);
        }
        Node current = field.getValueNode();
        if ("SET_SCALAR".equals(edit.operation())) {
            String value = scalarValue(edit.field(), edit.value());
            if (!(current instanceof ScalarNode scalar) || Tag.NULL.equals(scalar.getTag())) throw invalid();
            if (edit.field().endsWith(".Material") && !Tag.STR.equals(scalar.getTag())) throw invalid();
            if (edit.field().endsWith(".Amount") && !Tag.INT.equals(scalar.getTag())) throw invalid();
            int start = offset(content, current.getStartMark().getIndex());
            int end = offset(content, current.getEndMark().getIndex());
            if (start < 0 || end < start || end > content.length() || content.substring(start, end).contains("\n")) throw invalid();
            return content.substring(0, start) + value + content.substring(end);
        }
        if (!(current instanceof SequenceNode sequence) || sequence.getFlowStyle() != DumperOptions.FlowStyle.BLOCK
                || sequence.getValue().isEmpty() || sequence.getValue().size() > MAX_LIST) throw invalid();
        for (Node node : sequence.getValue()) if (!(node instanceof ScalarNode scalar) || !Tag.STR.equals(scalar.getTag())) throw invalid();
        if ("REPLACE_LIST".equals(edit.operation())) {
            if (!(edit.value() instanceof List<?> requested) || requested.size() > MAX_LIST) throw invalid();
            List<String> replacement = new ArrayList<>();
            for (Object value : requested) {
                if (!(value instanceof String line)) throw invalid();
                validateText(line); replacement.add(line);
            }
            int start = lineStart(content, offset(content, field.getKeyNode().getStartMark().getIndex()));
            int end = afterLine(content, offset(content, sequence.getValue().get(sequence.getValue().size() - 1).getEndMark().getIndex()));
            if (content.substring(start, end).contains("#")) throw invalid(); // comments inside this field must not be erased
            int keyIndent = field.getKeyNode().getStartMark().getColumn();
            int itemIndent = lineIndent(content, offset(content, sequence.getValue().get(0).getStartMark().getIndex()));
            StringBuilder text = new StringBuilder(spaces(keyIndent)).append(edit.field()).append(replacement.isEmpty() ? ": []\n" : ":\n");
            replacement.forEach(line -> text.append(spaces(itemIndent)).append("- ").append(quote(line)).append('\n'));
            return content.substring(0, start) + text + content.substring(end);
        }
        if (!(edit.value() instanceof String entry)) throw invalid();
        validateText(entry);
        if ("APPEND_LIST_ENTRY".equals(edit.operation())) {
            if (sequence.getValue().size() >= MAX_LIST) throw invalid();
            int at = sequence.getValue().isEmpty()
                    ? afterLine(content, offset(content, field.getKeyNode().getStartMark().getIndex()))
                    : afterLine(content, offset(content, sequence.getValue().get(sequence.getValue().size() - 1).getEndMark().getIndex()));
            int indent = sequence.getValue().isEmpty() ? field.getKeyNode().getStartMark().getColumn() : lineIndent(content, offset(content, sequence.getValue().get(0).getStartMark().getIndex()));
            return insert(content, at, spaces(indent) + "- " + quote(entry) + "\n");
        }
        if ("REMOVE_LIST_ENTRY".equals(edit.operation())) {
            List<Node> matches = sequence.getValue().stream().filter(node -> entry.equals(((ScalarNode) node).getValue())).toList();
            if (matches.size() != 1) throw invalid(); // duplicates need an explicit index, never guess
            int start = lineStart(content, offset(content, matches.get(0).getStartMark().getIndex()));
            int end = afterLine(content, start);
            if (!content.substring(start, end).stripLeading().startsWith("-")) throw invalid();
            if (sequence.getValue().size() == 1) {
                int keyStart = lineStart(content, offset(content, field.getKeyNode().getStartMark().getIndex()));
                if (content.substring(keyStart, end).contains("#")) throw invalid();
                return content.substring(0, keyStart) + spaces(field.getKeyNode().getStartMark().getColumn())
                        + edit.field() + ": []\n" + content.substring(end);
            }
            return content.substring(0, start) + content.substring(end);
        }
        throw invalid();
    }

    private static String addMissingField(String content, MappingNode reward, NodeTuple rewardTuple, Edit edit) {
        String field = edit.field();
        if (field.contains(".")) throw invalid(); // never synthesize a possibly conflicting parent map
        String value;
        if ("SET_SCALAR".equals(edit.operation())) value = scalarValue(field, edit.value());
        else {
            if (!(edit.value() instanceof String text)) throw invalid();
            validateText(text);
            value = quote(text);
        }
        int indent = rewardTuple == null ? 0 : childIndent(reward, rewardTuple.getKeyNode().getStartMark().getColumn() + 2);
        int at = rewardTuple == null ? content.length() : blockEnd(content,
                afterLine(content, offset(content, rewardTuple.getKeyNode().getStartMark().getIndex())),
                rewardTuple.getKeyNode().getStartMark().getColumn());
        String line = spaces(indent) + field + ":" + ("SET_SCALAR".equals(edit.operation()) ? " " + value + "\n" : "\n" + spaces(indent) + "- " + value + "\n");
        return insert(content, at, rewardTuple == null && !content.isEmpty() && !content.endsWith("\n") ? "\n" + line : line);
    }

    private static boolean namedRewardFile(String fileName) {
        return fileName != null && fileName.matches("Rewards/[A-Za-z0-9][A-Za-z0-9_-]{0,99}\\.yml");
    }

    private static void walkRewards(List<Scope> result, MappingNode mapping, String prefix, int depth) {
        if (depth > 12 || result.size() >= MAX_REWARDS) throw invalid();
        for (NodeTuple tuple : mapping.getValue()) {
            String name = key(tuple.getKeyNode());
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            if (Set.of("Rewards", "EverySiteReward", "AnySiteRewards", "LostRewards").contains(name))
                addScope(result, path, tuple.getValueNode(), false);
            if (tuple.getValueNode() instanceof MappingNode child && block(child)) walkRewards(result, child, path, depth + 1);
        }
    }

    private static void addScope(List<Scope> result, String path, Node node, boolean editable) {
        if (result.size() >= MAX_REWARDS) throw invalid();
        if (node == null) {
            result.add(new Scope(path, "MISSING", editable, Map.of(), List.of(), List.of())); return;
        }
        if (!(node instanceof MappingNode reward)) {
            result.add(new Scope(path, "UNSUPPORTED", false, Map.of(), List.of(), List.of())); return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        List<String> advanced = new ArrayList<>();
        for (NodeTuple tuple : reward.getValue()) {
            String name = key(tuple.getKeyNode());
            Node value = tuple.getValueNode();
            if ("Commands".equals(name) && value instanceof SequenceNode list && stringList(list) != null) fields.put(name, stringList(list));
            else if ("Messages".equals(name) && value instanceof MappingNode messages) {
                for (NodeTuple message : messages.getValue()) {
                    String label = key(message.getKeyNode());
                    if (Set.of("Player", "Broadcast").contains(label) && message.getValueNode() instanceof ScalarNode scalar && Tag.STR.equals(scalar.getTag())) fields.put("Messages." + label, scalar.getValue());
                    else advanced.add("Messages." + label);
                }
            } else if (Set.of("Money", "Chance").contains(name) && value instanceof ScalarNode scalar && (Tag.INT.equals(scalar.getTag()) || Tag.FLOAT.equals(scalar.getTag()))) fields.put(name, scalar.getValue());
            else if ("Items".equals(name) && value instanceof MappingNode items && block(items)) {
                addItemFields(fields, items);
                advanced.add(name); // metadata and unsupported item shapes remain Advanced-only
            }
            else advanced.add(name);
        }
        List<String> tree = new ArrayList<>();
        structure(reward, "", 0, tree);
        result.add(new Scope(path, reward.getValue().isEmpty() ? "EMPTY" : "PRESENT", editable && block(reward),
                Map.copyOf(fields), List.copyOf(advanced), List.copyOf(tree)));
    }

    private static void structure(Node node, String prefix, int depth, List<String> result) {
        if (depth > 8 || result.size() > 120) throw invalid();
        if (!(node instanceof MappingNode mapping)) return;
        for (NodeTuple tuple : mapping.getValue()) {
            String name = key(tuple.getKeyNode());
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            result.add(tuple.getValueNode() instanceof SequenceNode ? path + "[]" : path);
            if (result.size() > 120) throw invalid();
            if (tuple.getValueNode() instanceof MappingNode child) structure(child, path, depth + 1, result);
        }
    }

    private static List<String> stringList(SequenceNode node) {
        if (node.getValue().size() > MAX_LIST) return null;
        List<String> result = new ArrayList<>();
        for (Node child : node.getValue()) {
            if (!(child instanceof ScalarNode scalar) || !Tag.STR.equals(scalar.getTag())) return null;
            result.add(scalar.getValue());
        }
        return result;
    }

    /**
     * Exposes only stable item leaves. Metadata remains out of the typed browser state and a
     * source-span replacement changes only the selected scalar, leaving it intact.
     */
    private static void addItemFields(Map<String, Object> fields, MappingNode items) {
        if (items.getValue().size() > 20) return;
        for (NodeTuple item : items.getValue()) {
            String itemKey = key(item.getKeyNode());
            if (!ITEM_KEY.matcher(itemKey).matches() || !(item.getValueNode() instanceof MappingNode itemMap) || !block(itemMap)) continue;
            Node material = child(itemMap, "Material");
            if (material instanceof ScalarNode scalar && Tag.STR.equals(scalar.getTag())) fields.put("Items." + itemKey + ".Material", scalar.getValue());
            Node amount = child(itemMap, "Amount");
            if (amount instanceof ScalarNode scalar && Tag.INT.equals(scalar.getTag())) fields.put("Items." + itemKey + ".Amount", scalar.getValue());
        }
    }

    private static MappingNode parse(String content) {
        if (content == null || content.length() > MAX_SOURCE || content.indexOf('\0') >= 0) throw invalid();
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(0); options.setNestingDepthLimit(40); options.setCodePointLimit(MAX_SOURCE);
        List<Node> docs = new ArrayList<>();
        try {
            for (Node node : new Yaml(new SafeConstructor(options)).composeAll(new StringReader(content))) docs.add(node);
        } catch (RuntimeException error) { throw invalid(); }
        if (docs.size() != 1 || !(docs.get(0) instanceof MappingNode root) || !block(root)) throw invalid();
        rejectAmbiguity(root);
        return root;
    }

    private static void rejectAmbiguity(Node node) {
        if (node.getAnchor() != null || node instanceof AnchorNode) throw invalid();
        if (node instanceof MappingNode mapping) {
            if (mapping.isMerged()) throw invalid();
            Set<String> seen = new java.util.HashSet<>();
            for (NodeTuple tuple : mapping.getValue()) {
                if (!seen.add(key(tuple.getKeyNode()).toLowerCase(java.util.Locale.ROOT))) throw invalid();
                rejectAmbiguity(tuple.getValueNode());
            }
        } else if (node instanceof SequenceNode sequence) for (Node child : sequence.getValue()) rejectAmbiguity(child);
    }

    private static Node child(MappingNode mapping, String name) { NodeTuple tuple = tuple(mapping, name); return tuple == null ? null : tuple.getValueNode(); }
    private static NodeTuple tuple(MappingNode mapping, String name) {
        for (NodeTuple tuple : mapping.getValue()) if (name.equals(key(tuple.getKeyNode()))) return tuple;
        return null;
    }
    private static NodeTuple findTuple(MappingNode mapping, String field) {
        String[] parts = field.split("\\."); MappingNode parent = mapping; NodeTuple item = null;
        for (int i = 0; i < parts.length; i++) {
            item = tuple(parent, parts[i]);
            if (item == null) return null;
            if (i < parts.length - 1) {
                if (!(item.getValueNode() instanceof MappingNode next) || !block(next)) throw invalid();
                parent = next;
            }
        }
        return item;
    }
    private static String key(Node node) { if (!(node instanceof ScalarNode scalar) || scalar.getAnchor() != null) throw invalid(); return scalar.getValue(); }
    private static boolean block(MappingNode node) { return node.getFlowStyle() == DumperOptions.FlowStyle.BLOCK; }
    private static int childIndent(MappingNode node, int fallback) { return node.getValue().isEmpty() ? fallback : node.getValue().get(0).getKeyNode().getStartMark().getColumn(); }
    private static int offset(String source, int codePoints) {
        if (codePoints < 0 || codePoints > source.codePointCount(0, source.length())) throw invalid();
        return source.offsetByCodePoints(0, codePoints);
    }
    private static int lineStart(String source, int at) { int pos = Math.min(at, source.length()); while (pos > 0 && source.charAt(pos - 1) != '\n') pos--; return pos; }
    private static int afterLine(String source, int at) { int end = source.indexOf('\n', lineStart(source, at)); return end < 0 ? source.length() : end + 1; }
    private static int lineIndent(String source, int at) { int pos = lineStart(source, at); while (pos < source.length() && source.charAt(pos) == ' ') pos++; return pos - lineStart(source, at); }
    private static int blockEnd(String source, int at, int parentIndent) {
        while (at < source.length()) {
            int end = afterLine(source, at); int cursor = at;
            while (cursor < end && source.charAt(cursor) == ' ') cursor++;
            if (cursor < end && source.charAt(cursor) == '\t') throw invalid();
            if (cursor < end && source.charAt(cursor) != '#' && source.charAt(cursor) != '\n' && source.charAt(cursor) != '\r'
                    && cursor - at <= parentIndent) return at;
            at = end;
        }
        return source.length();
    }
    private static String insert(String source, int at, String text) { return source.substring(0, at) + (at > 0 && source.charAt(at - 1) != '\n' ? "\n" : "") + text + source.substring(at); }
    private static String spaces(int size) { return " ".repeat(Math.max(0, size)); }
    private static void validateText(String value) {
        if (value == null || value.isBlank() || value.length() > 500) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 0x20 && character != '\n' && character != '\r' && character != '\t') throw invalid();
        }
    }
    private static String scalarValue(String field, Object value) {
        if (Set.of("Money", "Chance").contains(field)) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0) throw invalid();
            return number.toString();
        }
        if (field.endsWith(".Amount")) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() != Math.rint(number.doubleValue()) || number.doubleValue() < 1 || number.doubleValue() > 64) throw invalid();
            return Long.toString(number.longValue());
        }
        if (field.endsWith(".Material")) {
            if (!(value instanceof String material) || !MATERIAL.matcher(material).matches()) throw invalid();
            return quote(material);
        }
        if (!(value instanceof String text)) throw invalid(); validateText(text); return quote(text);
    }
    private static boolean itemField(String field) {
        String[] parts = field.split("\\.", -1);
        return parts.length == 3 && "Items".equals(parts[0]) && ITEM_KEY.matcher(parts[1]).matches()
                && Set.of("Material", "Amount").contains(parts[2]);
    }
    private static String quote(String text) { return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Reward structure or edit is unsupported or ambiguous"); }

    public record Scope(String path, String status, boolean editable, Map<String, Object> fields,
            List<String> advancedKeys, List<String> structurePaths) { }
    public record Edit(String operation, String field, Object value) { }
}
