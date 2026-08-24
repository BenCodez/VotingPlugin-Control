package com.bencodez.votingplugin.control.protocol;

public record RequestEnvelope<T>(int protocolVersion, String correlationId, String operation, T payload) { }
