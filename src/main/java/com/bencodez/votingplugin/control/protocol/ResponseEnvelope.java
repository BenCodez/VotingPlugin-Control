package com.bencodez.votingplugin.control.protocol;

public record ResponseEnvelope<T>(int protocolVersion, String correlationId, boolean success, T payload,
                                  ProtocolError error) { }
