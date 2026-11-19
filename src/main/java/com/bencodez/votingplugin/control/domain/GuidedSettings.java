package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/** Typed preset values and presence evidence from an existing masked managed-file READ. */
public enum GuidedSettings {
    COMMON("common-settings", "Config.yml", List.of(
            new Field("processRewards", "ProcessRewards", "true", Kind.FLAG, true),
            new Field("autoCreateVoteSites", "AutoCreateVoteSites", "true", Kind.FLAG, false),
            new Field("extraAllSitesCheck", "ExtraAllSitesCheck", "false", Kind.FLAG, true),
            new Field("countFakeVotes", "CountFakeVotes", "true", Kind.FLAG, false),
            new Field("disableNoServiceSiteMessage", "DisableNoServiceSiteMessage", "false", Kind.FLAG, true),
            new Field("disableUpdateChecking", "DisableUpdateChecking", "false", Kind.FLAG, false))),
    LOG("vote-logging", "Config.yml", List.of(
            new Field("enabled", "VoteLogging.Enabled", "false", Kind.FLAG, true),
            new Field("purgeDays", "VoteLogging.PurgeDays", "30", Kind.RETENTION, true),
            new Field("useMainMySQL", "VoteLogging.UseMainMySQL", "true", Kind.FLAG, false))),
    PARTY("vote-party", "SpecialRewards.yml", List.of(
            new Field("enabled", "VoteParty.Enabled", "false", Kind.FLAG, true),
            new Field("votesRequired", "VoteParty.VotesRequired", "20", Kind.THRESHOLD, true),
            new Field("giveAllPlayers", "VoteParty.GiveAllPlayers", "false", Kind.FLAG, true),
            new Field("onlineOnly", "VoteParty.GiveOnlinePlayersOnly", "true", Kind.FLAG, true),
            new Field("broadcast", "VoteParty.Broadcast", "", Kind.TEXT, true)));

    private final String preset;
    private final String file;
    private final List<Field> fields;

    GuidedSettings(String preset, String file, List<Field> fields) {
        this.preset = preset;
        this.file = file;
        this.fields = fields;
    }

    public static GuidedSettings select(String preset) {
        for (var entry : values()) if (entry.preset.equals(preset)) return entry;
        throw new IllegalArgumentException("Select a supported guided setup preset");
    }

    public String action() { return preset; }
    String file() { return file; }
    String capability() { return selector().capability(); }

    ManagedConfiguration selector() {
        return configuration(this == PARTY ? Map.of("enabled", "false") : Map.of());
    }

    ManagedConfiguration configuration(Map<String, String> options) {
        var value = new ManagedConfiguration(ManagedConfiguration.QUICK_SETUP, null, List.of(), null, null, preset, options);
        value.validateProposal();
        return value;
    }

    public static Set<String> resultKeys() {
        return Set.of("processRewards", "autoCreateVoteSites", "extraAllSitesCheck", "countFakeVotes",
                "disableNoServiceSiteMessage", "disableUpdateChecking", "enabled", "purgeDays",
                "useMainMySQL", "votesRequired", "giveAllPlayers", "onlineOnly", "broadcast");
    }

    Map<String, String> effectiveValues(ManagedConfiguration result) {
        if (result == null) throw new IllegalArgumentException("Missing typed preset response");
        var values = new LinkedHashMap<String, String>();
        for (var field : fields) {
            String value = result.options().get(field.key());
            if (!field.kind().valid(value)) throw new IllegalArgumentException("Unsupported preset READ result");
            values.put(field.key(), value);
        }
        return Map.copyOf(values);
    }

    Evidence evidence(String content, Map<String, String> effective, Map<String, Object> overrides) {
        if (content == null || overrides == null || overrides.isEmpty() || overrides.size() > fields.size()) {
            throw new IllegalArgumentException("Guided setup requires at least one typed change");
        }
        for (String key : overrides.keySet()) {
            if (fields.stream().noneMatch(field -> field.editable() && field.key().equals(key))) {
                throw new IllegalArgumentException("This field is managed in a different Control editor");
            }
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setNestingDepthLimit(32);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(ManagedConfiguration.MAX_CONTENT);
        Node root;
        try {
            root = new Yaml(new SafeConstructor(options)).compose(new StringReader(content));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Managed file is ambiguous", invalid);
        }
        if (!(root instanceof MappingNode)) throw new IllegalArgumentException("Managed file requires a mapping");
        var proposal = new LinkedHashMap<String, String>();
        var before = new LinkedHashMap<String, String>();
        var after = new LinkedHashMap<String, String>();
        for (var field : fields) {
            String typed = effective.get(field.key());
            String actual = readField(root, field);
            if (!field.kind().valid(typed) || !typed.equals(actual == null ? field.defaultValue() : actual)) {
                throw new IllegalArgumentException("Preset and managed-file values disagree");
            }
            boolean override = overrides.containsKey(field.key());
            if (field.kind() == Kind.TEXT && !override) continue;
            String proposed = override ? field.kind().request(overrides.get(field.key())) : typed;
            proposal.put(field.key(), proposed);
            if (actual != null && actual.equals(proposed)) continue;
            if (!override) throw new IllegalArgumentException("Preset would materialize an unselected key");
            before.put(field.path(), actual == null ? "(absent; effective " + typed + ")" : actual);
            after.put(field.path(), proposed);
        }
        return new Evidence(Map.copyOf(proposal), Map.copyOf(before), Map.copyOf(after));
    }

    private static String readField(Node root, Field field) {
        Node current = root;
        for (String part : field.path().split("[.]")) {
            if (!(current instanceof MappingNode mapping) || current.getAnchor() != null
                    || !Tag.MAP.equals(current.getTag())) throw new IllegalArgumentException("Unsupported key parent");
            Node match = null;
            for (var tuple : mapping.getValue()) {
                if (!(tuple.getKeyNode() instanceof ScalarNode scalar) || !Tag.STR.equals(scalar.getTag())
                        || scalar.getValue().indexOf('.') >= 0) throw new IllegalArgumentException("Ambiguous mapping");
                if (!part.equalsIgnoreCase(scalar.getValue())) continue;
                if (match != null || !part.equals(scalar.getValue())) throw new IllegalArgumentException("Ambiguous key");
                match = tuple.getValueNode();
            }
            if (match == null) return null;
            current = match;
        }
        if (!(current instanceof ScalarNode scalar) || scalar.getAnchor() != null
                || !field.kind().tag.equals(scalar.getTag()) || !field.kind().valid(scalar.getValue())) {
            throw new IllegalArgumentException("Unsupported managed value");
        }
        return scalar.getValue();
    }

    record Evidence(Map<String, String> options, Map<String, String> before, Map<String, String> after) { }

    private record Field(String key, String path, String defaultValue, Kind kind, boolean editable) { }

    private enum Kind {
        FLAG(Tag.BOOL), RETENTION(Tag.INT), THRESHOLD(Tag.INT), TEXT(Tag.STR);

        private final Tag tag;
        Kind(Tag tag) { this.tag = tag; }

        boolean valid(String value) {
            if (value == null) return false;
            return switch (this) {
                case FLAG -> "true".equals(value) || "false".equals(value);
                case RETENTION -> "-1".equals(value) || integer(value, 3650);
                case THRESHOLD -> integer(value, 100000);
                case TEXT -> value.length() <= 500 && value.codePoints().noneMatch(Character::isISOControl);
            };
        }

        private static boolean integer(String value, int max) {
            return value.matches("[1-9][0-9]{0,5}") && Integer.parseInt(value) <= max;
        }

        String request(Object value) {
            boolean correctType = switch (this) {
                case FLAG -> value instanceof Boolean;
                case RETENTION, THRESHOLD -> value instanceof Integer;
                case TEXT -> value instanceof String text && !text.isBlank();
            };
            if (!correctType || !valid(value.toString())) throw new IllegalArgumentException("Invalid guided field value");
            return value.toString();
        }
    }
}
