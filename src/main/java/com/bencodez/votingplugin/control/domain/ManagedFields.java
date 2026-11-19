package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/** Explicit VotingPlugin field catalog and source-span edits over existing managed files. */
public final class ManagedFields {
    public static final int MAX_EDITS = 8;
    private static final int MAX_LIST = 24;
    private static final int MAX_LINE = 240;
    private static final Map<String, List<Field>> CATALOG = Map.of(
            "Config.yml", List.of(
                    flag("CreateBackups"), flag("VoteCommandEnabled"), flag("TreatVanishAsOffline"),
                    flag("QueueVotesDuringTimeChange"), flag("VoteReminderOptions.Enabled"),
                    flag("VoteReminderOptions.StopAfterMatch"), flag("AutomaticTimeChanges.Day"),
                    flag("AutomaticTimeChanges.Week"), flag("AutomaticTimeChanges.Month"),
                    flag("OfflineVotesLimit.Enabled"), integer("OfflineVotesLimit.Amount", 0, 10000),
                    integer("VoteBroadcast.MaxSitesListed", 0, 200),
                    integer("PointsOnVote", 0, 100000), integer("LimitVotePoints", -1, 100000000),
                    identifiers("DefaultRewardWorlds"), identifiers("DefaultRewardBlackListedWorlds"),
                    browserInteger("Database.MaxConnections", 1, 100)),
            "BungeeSettings.yml", List.of(
                    flag("PerServerRewards"), flag("PerServerPoints"), flag("TriggerVotifierEvent"),
                    flag("GiveExtraAllSitesRewards"), flag("Redis.SSL"),
                    browserInteger("GlobalData.MaxConnections", 1, 100),
                    browserList("BungeeVotePartyGlobalCommands")),
            "SpecialRewards.yml", List.of(
                    flag("VoteParty.ResetEachDay"), flag("VoteParty.ResetWeekly"), flag("VoteParty.ResetMonthly"),
                    flag("VoteParty.OnlyOncePerDay"), flag("VoteParty.OnlyOncePerWeek"),
                    flag("VoteParty.ResetExtraVotesWeekly"), flag("VoteParty.ResetExtraVotesMonthly"),
                    flag("VoteParty.CountFakeVotes"), flag("VoteParty.CountOfflineVotes"),
                    integer("VoteParty.UserVotesRequired", 0, 100000), flag("OnlyOneCumulative"),
                    browserList("VoteParty.GlobalCommands"), browserList("VoteParty.Rewards.Commands"),
                    browserList("VoteParty.VoteReminder.Commands")),
            "bungeeconfig.yml", List.of(
                    flag("ProxyBroadcast.Enabled"), identifiers("ProxyBroadcast.Scope.Servers"),
                    choices("ProxyBroadcast.Scope.Mode", "PLAYER_SERVER", "ALL_SERVERS", "SERVERS", "ALL_EXCEPT"),
                    choices("ProxyBroadcast.OfflineMode", "NONE", "QUEUE", "FORWARD"),
                    identifiers("ProxyBroadcast.OfflineForward.Servers"), identifiers("WhiteListedServers"),
                    flag("WaitForUserOnline"), integer("VoteCacheTime", -1, 3650),
                    integer("MaxAmountOfVotesPerDay", -1, 100000),
                    flag("AutomaticTimeChanges.Day"), flag("AutomaticTimeChanges.Week"),
                    flag("AutomaticTimeChanges.Month"), flag("VoteParty.SendToAllServers"),
                    identifiers("VoteParty.ServersToSend"),
                    integer("VoteParty.IncreaseVotesRequired", 0, 100000),
                    browserInteger("Database.MaxConnections", 1, 100),
                    browserInteger("GlobalData.MaxConnections", 1, 100), browserList("VoteParty.BungeeCommands")),
            "GUI.yml", List.of(flag("CHEST.VoteURL.BackButton"), flag("CHEST.VoteURL.ViewAllUrlsButtonEnabled"),
                    flag("CHEST.VoteURL.AllUrlsButton.RequireAllSitesVoted"),
                    integer("CHEST.VoteGUI.URL.Item.Amount", 1, 64), integer("CHEST.VoteGUI.Shop.Item.Amount", 1, 64),
                    browserList("CHEST.VoteGUI.URL.Item.Lore"), browserList("CHEST.VoteGUI.Shop.Item.Lore")),
            "Shop.yml", List.of(flag("VoteShop.Enabled"), flag("VoteShop.BackButton"),
                    flag("VoteShop.HideLimitedReached"), flag("VoteShop.RequireConfirmation"),
                    flag("VoteShop.ReopenGUIOnPurchase"),
                    integer("VoteShop.BackButtonItem.Amount", 1, 64),
                    integer("VoteShop.BackButtonItem.Slot", 0, 53)));

    private ManagedFields() { }

    public enum Kind { BOOLEAN, INTEGER, CHOICE, IDENTIFIER_LIST, TEXT_LIST }

    public record Field(String path, Kind kind, Integer minimum, Integer maximum, List<String> choices, boolean browserOnly) { }

    public record Entry(Field field, Object value) { }

    record Patch(String content, Map<String, String> before, Map<String, String> after, Set<String> changes) { }

    private record Located(Field field, Object value, int start, int end, String prefix) { }

    public static List<String> files() { return CATALOG.keySet().stream().sorted().toList(); }

    public static Set<String> agentPaths() {
        var paths = new HashSet<String>();
        CATALOG.values().forEach(fields -> fields.stream().filter(field -> !field.browserOnly()).forEach(field -> paths.add(field.path())));
        return Set.copyOf(paths);
    }

    public static List<Field> catalog(String file, boolean agent) {
        var fields = file == null ? null : CATALOG.get(file);
        if (fields == null) throw invalid("Choose an allowlisted configuration file");
        return fields.stream().filter(field -> !agent || !field.browserOnly()).toList();
    }

    static List<Entry> read(String file, String document, boolean agent) {
        return locate(file, document, agent).values().stream().map(value -> new Entry(value.field(), value.value())).toList();
    }

    static Patch patch(String file, String document, Map<String, Object> overrides, boolean agent) {
        if (overrides == null || overrides.isEmpty() || overrides.size() > MAX_EDITS) throw invalid("Choose one to eight typed fields");
        var locations = locate(file, document, agent);
        var before = new LinkedHashMap<String, String>();
        var after = new LinkedHashMap<String, String>();
        var edits = new ArrayList<Splice>();
        for (var request : overrides.entrySet()) {
            var location = locations.get(request.getKey());
            if (location == null) throw invalid("Selected field is absent, unsupported, or outside this editor");
            Object proposed = normalize(location.field(), request.getValue());
            if (proposed.equals(location.value())) continue;
            before.put(request.getKey(), display(location.value()));
            after.put(request.getKey(), display(proposed));
            edits.add(new Splice(location.start(), location.end(), location.prefix() + encode(proposed)));
        }
        if (edits.isEmpty()) throw invalid("No typed field changed");
        edits.sort(Comparator.comparingInt(Splice::start).reversed());
        var patched = new StringBuilder(document);
        int previous = document.length();
        for (var edit : edits) {
            if (edit.end() > previous) throw invalid("Overlapping YAML values");
            patched.replace(edit.start(), edit.end(), edit.value());
            previous = edit.start();
        }
        String result = patched.toString();
        var confirmed = locate(file, result, agent);
        for (var entry : after.entrySet()) {
            var location = confirmed.get(entry.getKey());
            if (location == null || !entry.getValue().equals(display(location.value()))) throw invalid("Typed YAML edit could not be verified");
        }
        Set<String> diff = after.keySet().stream().map(path -> "changed " + path).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Patch(result, Map.copyOf(before), Map.copyOf(after), diff);
    }

    static String display(Object value) {
        // Use the same escaped representation for browser review and MCP projection.
        return value instanceof List<?> ? encode(value) : value.toString();
    }

    private static Map<String, Located> locate(String file, String content, boolean agent) {
        var fields = catalog(file, agent);
        if (content == null || content.length() > ManagedConfiguration.MAX_CONTENT || content.indexOf('\0') >= 0) {
            throw invalid("Managed document exceeds the typed editor limit");
        }
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(32);
        options.setCodePointLimit(ManagedConfiguration.MAX_CONTENT);
        options.setMergeOnCompose(false);
        Node root;
        try {
            root = new Yaml(new SafeConstructor(options)).compose(new StringReader(content));
        } catch (RuntimeException malformed) {
            throw invalid("Managed document does not contain safe, unambiguous YAML");
        }
        if (!(root instanceof MappingNode)) throw invalid("Managed document must be a YAML mapping");
        var result = new LinkedHashMap<String, Located>();
        for (var field : fields) {
            Node current = root;
            ScalarNode key = null;
            for (String part : field.path().split("[.]")) {
                if (!(current instanceof MappingNode mapping)) {
                    current = null;
                    break;
                }
                Keyed entry = child(mapping, part);
                if (entry == null) {
                    current = null;
                    break;
                }
                key = entry.key();
                current = entry.value();
            }
            if (current == null || current.getAnchor() != null) continue;
            Object value = decode(field, current);
            if (value == null) continue;
            int start = offset(content, current.getStartMark().getIndex());
            Node last = current;
            String prefix = "";
            if (current instanceof SequenceNode sequence && !sequence.getValue().isEmpty()
                    && sequence.getFlowStyle() == org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK) {
                last = sequence.getValue().get(sequence.getValue().size() - 1);
                if (current.getStartMark().getColumn() == key.getStartMark().getColumn()) prefix = "  ";
            }
            int end = offset(content, last.getEndMark().getIndex());
            if (start >= end || content.substring(start, end).contains("#")) continue;
            result.put(field.path(), new Located(field, value, start, end, prefix));
        }
        return result;
    }

    private static Keyed child(MappingNode mapping, String part) {
        if (mapping.getAnchor() != null || !Tag.MAP.equals(mapping.getTag())) throw invalid("Unsupported YAML mapping");
        var keys = new HashSet<String>();
        Keyed match = null;
        for (var tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key) || !Tag.STR.equals(key.getTag())
                    || !keys.add(key.getValue().toLowerCase(Locale.ROOT)) || key.getValue().contains(".")) {
                throw invalid("Duplicate, compound, or unsupported YAML key");
            }
            if (part.equalsIgnoreCase(key.getValue())) {
                if (!part.equals(key.getValue())) throw invalid("Noncanonical YAML key for a managed field");
                match = new Keyed(key, tuple.getValueNode());
            }
        }
        return match;
    }

    private static Object decode(Field field, Node node) {
        if (node instanceof SequenceNode sequence && Tag.SEQ.equals(sequence.getTag())
                && (field.kind() == Kind.IDENTIFIER_LIST || field.kind() == Kind.TEXT_LIST)) {
            if (sequence.getValue().size() > MAX_LIST) return null;
            var items = new ArrayList<String>();
            for (Node item : sequence.getValue()) {
                if (!(item instanceof ScalarNode scalar) || !Tag.STR.equals(scalar.getTag()) || scalar.getAnchor() != null
                        || scalar.getStartMark().getLine() != scalar.getEndMark().getLine()) return null;
                items.add(scalar.getValue());
            }
            return tryNormalize(field, items);
        }
        if (!(node instanceof ScalarNode scalar) || scalar.getStartMark().getLine() != scalar.getEndMark().getLine()) return null;
        String text = scalar.getValue();
        return switch (field.kind()) {
            case BOOLEAN -> Tag.BOOL.equals(scalar.getTag()) && Set.of("true", "false").contains(text)
                    ? Boolean.valueOf(text) : null;
            case INTEGER -> Tag.INT.equals(scalar.getTag()) && text.matches("-?(0|[1-9][0-9]{0,8})")
                    ? tryNormalize(field, Integer.parseInt(text)) : null;
            case CHOICE -> Tag.STR.equals(scalar.getTag()) ? tryNormalize(field, text) : null;
            default -> null;
        };
    }

    private static Object tryNormalize(Field field, Object value) {
        try {
            return normalize(field, value);
        } catch (IllegalArgumentException unsupported) {
            return null;
        }
    }

    private static Object normalize(Field field, Object value) {
        switch (field.kind()) {
            case BOOLEAN:
                if (value instanceof Boolean) return value;
                break;
            case INTEGER:
                if (value instanceof Integer number && number >= field.minimum() && number <= field.maximum()) return value;
                break;
            case CHOICE:
                if (value instanceof String text && field.choices().contains(text)) return text;
                break;
            case IDENTIFIER_LIST, TEXT_LIST:
                if (value instanceof List<?> items && items.size() <= MAX_LIST && items.stream().allMatch(item ->
                        item instanceof String text && validLine(field.kind(), text))) return List.copyOf(items);
                break;
        }
        throw invalid("Typed field value is outside the supported schema");
    }

    private static boolean validLine(Kind kind, String text) {
        return !text.isBlank() && text.length() <= MAX_LINE && text.codePoints().noneMatch(Character::isISOControl)
                && !text.contains("__VOTINGPLUGIN_CONTROL_REDACTED__")
                && (kind == Kind.TEXT_LIST || text.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"));
    }

    private static String encode(Object value) {
        if (value instanceof List<?> list) return list.stream().map(item -> quote((String) item))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        if (value instanceof String text) return quote(text);
        return value.toString();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static int offset(String source, int codePoints) { return source.offsetByCodePoints(0, codePoints); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    private static Field flag(String path) { return new Field(path, Kind.BOOLEAN, null, null, List.of(), false); }
    private static Field integer(String path, int min, int max) { return new Field(path, Kind.INTEGER, min, max, List.of(), false); }
    private static Field browserInteger(String path, int min, int max) { return new Field(path, Kind.INTEGER, min, max, List.of(), true); }
    private static Field identifiers(String path) { return new Field(path, Kind.IDENTIFIER_LIST, null, null, List.of(), false); }
    private static Field browserList(String path) { return new Field(path, Kind.TEXT_LIST, null, null, List.of(), true); }
    private static Field choices(String path, String... values) { return new Field(path, Kind.CHOICE, null, null, List.of(values), false); }

    private record Keyed(ScalarNode key, Node value) { }
    private record Splice(int start, int end, String value) { }
}
