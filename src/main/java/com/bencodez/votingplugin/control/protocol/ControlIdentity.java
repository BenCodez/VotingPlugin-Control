package com.bencodez.votingplugin.control.protocol;

import java.util.UUID;

public record ControlIdentity(UUID instanceId, String applicationVersion, int protocolVersion) { }
