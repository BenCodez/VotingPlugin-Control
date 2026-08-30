package com.bencodez.votingplugin.control.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned union of the configuration domains negotiated with VotingPlugin nodes. */
public record ManagedConfiguration(String domain, Boolean sendVotesToAllServers, List<String> blockedServers,
                                   String fileName, String content, String preset, Map<String, String> options) {
    public static final String PROXY_ROUTING = "proxy-routing";
    public static final String FILE = "file";
    public static final String QUICK_SETUP = "quick-setup";
    public static final String VOTE_SITES_SYNC = "sync-vote-sites";
    public static final String COMMUNICATION_TEST = "communication-test";
    public static final String PROXY_METHOD = "proxy-method";
    public static final int MAX_CONTENT = 512 * 1024;

    public ManagedConfiguration {
        domain = domain == null && sendVotesToAllServers != null ? PROXY_ROUTING : domain;
        blockedServers = blockedServers == null ? List.of() : List.copyOf(blockedServers);
        options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
        if (!List.of(PROXY_ROUTING, FILE, QUICK_SETUP).contains(domain)) {
            throw new IllegalArgumentException("configuration domain is unsupported");
        }
        if (PROXY_ROUTING.equals(domain)) {
            if (sendVotesToAllServers == null || fileName != null || content != null || preset != null || !options.isEmpty())
                throw new IllegalArgumentException("proxy routing contains fields from another domain");
            new ProxyRoutingConfiguration(sendVotesToAllServers, blockedServers);
        } else if (FILE.equals(domain)) {
            if (sendVotesToAllServers != null || !blockedServers.isEmpty() || preset != null || !options.isEmpty())
                throw new IllegalArgumentException("file configuration contains fields from another domain");
            validateFileName(fileName);
            if (content != null && (content.length() > MAX_CONTENT || content.indexOf('\0') >= 0)) {
                throw new IllegalArgumentException("configuration file content is invalid");
            }
        } else {
            if (sendVotesToAllServers != null || !blockedServers.isEmpty() || fileName != null || content != null) {
                throw new IllegalArgumentException("quick setup contains fields from another domain");
            }
            if (preset == null || !preset.matches("[a-z][a-z0-9-]{0,39}")) {
                throw new IllegalArgumentException("quick setup preset is invalid");
            }
            if (options.size() > 20 || options.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                    || !entry.getKey().matches("[a-z][A-Za-z0-9]{0,39}") || entry.getValue() == null
                    || invalidOption(entry.getKey(), entry.getValue(), VOTE_SITES_SYNC.equals(preset)))) {
                throw new IllegalArgumentException("quick setup options are invalid");
            }
        }
    }

    public static ManagedConfiguration proxy(ProxyRoutingConfiguration value) {
        return new ManagedConfiguration(PROXY_ROUTING, value.sendVotesToAllServers(), value.blockedServers(),
                null, null, null, Map.of());
    }

    public static ManagedConfiguration file(String fileName, String content) {
        return new ManagedConfiguration(FILE, null, List.of(), fileName, content, null, Map.of());
    }

    public void validateProposal() {
        if (QUICK_SETUP.equals(domain) && VOTE_SITES_SYNC.equals(preset)
                && (options.size() != 1 || !options.containsKey("sourceContent"))) {
            throw new IllegalArgumentException("VoteSites sync requires sourceContent");
        }
        if (QUICK_SETUP.equals(domain) && COMMUNICATION_TEST.equals(preset)
                && (options.size() != 1 || options.get("server") == null
                || !options.get("server").matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) {
            throw new IllegalArgumentException("communication test requires one valid server");
        }
        if (QUICK_SETUP.equals(domain) && PROXY_METHOD.equals(preset)
                && (options.size() != 1 || !List.of("PLUGINMESSAGING", "REDIS", "MQTT", "SOCKETS", "MYSQL")
                .contains(options.get("method")))) {
            throw new IllegalArgumentException("proxy method requires one supported method");
        }
    }

    public String capability() {
        return switch (domain) {
            case PROXY_ROUTING -> "config.proxy-routing.v1";
            case FILE -> "config.files.v1";
            case QUICK_SETUP -> VOTE_SITES_SYNC.equals(preset)
                    ? "config.vote-sites-sync.v1" : COMMUNICATION_TEST.equals(preset)
                    ? "config.transport-test.v1" : PROXY_METHOD.equals(preset)
                    ? "config.proxy-method.v1" : "config.quick-setup.v1";
            default -> throw new IllegalStateException("unsupported configuration domain");
        };
    }

    /** Omits file contents so proposals, including newly entered secrets, are never echoed by operation APIs. */
    public ManagedConfiguration publicView() {
        if (FILE.equals(domain)) return file(fileName, null);
        if (QUICK_SETUP.equals(domain) && VOTE_SITES_SYNC.equals(preset) && options.containsKey("sourceContent")) {
            Map<String, String> visible = new LinkedHashMap<>(options);
            visible.remove("sourceContent");
            return new ManagedConfiguration(domain, sendVotesToAllServers, blockedServers, fileName, content,
                    preset, visible);
        }
        return this;
    }

    private static boolean invalidOption(String name, String value, boolean voteSitesSync) {
        boolean sourceContent = voteSitesSync && "sourceContent".equals(name);
        int maximum = sourceContent ? MAX_CONTENT : 500;
        return value.indexOf('\0') >= 0 || value.length() > maximum;
    }

    private static void validateFileName(String value) {
        if (value == null || value.length() > 160
                || !value.matches("(?:Config|VoteSites|SpecialRewards|GUI|Shop|BungeeSettings)\\.yml|VoteSites/[A-Za-z0-9._-]{1,100}\\.yml")) {
            throw new IllegalArgumentException("configuration file name is not managed");
        }
    }
}
