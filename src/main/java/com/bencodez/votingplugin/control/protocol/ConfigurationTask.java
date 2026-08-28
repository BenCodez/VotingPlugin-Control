package com.bencodez.votingplugin.control.protocol;

import java.util.UUID;

public record ConfigurationTask(UUID operationId, String type, ManagedConfiguration configuration,
                                String expectedRevision, UUID attemptId) { }
