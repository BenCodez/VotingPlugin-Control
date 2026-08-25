package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bencodez.votingplugin.control.protocol.ConfigurationTask;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.ProxyRoutingConfiguration;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationOperationsTest {
    @TempDir Path directory;

    @Test void previewApprovalCarriesEachNodesRevisionIntoApplyAndIsSingleUse() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        registry.register(new NodeRegistration("proxy-a", UUID.randomUUID(), "Proxy A", "VELOCITY", "test", 1,
                Set.of("presence.snapshot", ConfigurationOperations.CAPABILITY), Set.of("presence.snapshot")));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        ProxyRoutingConfiguration proposal = new ProxyRoutingConfiguration(true, List.of("lobby"));

        ConfigurationOperations.OperationView preview = operations.createPreview(List.of("proxy-a"), proposal);
        ConfigurationTask previewTask = operations.claim("proxy-a");
        assertEquals("PREVIEW", previewTask.type());
        preview = operations.complete(preview.operationId(), "proxy-a",
                new ConfigurationTaskResult(registry.find("proxy-a").sessionId(), true, "OK", "valid", "a".repeat(64), proposal,
                        List.of("blockedServers changed"), false, false));
        assertEquals("SUCCEEDED", preview.state());
        assertNotNull(preview.approvalToken());

        UUID previewId = preview.operationId();
        String approvalToken = preview.approvalToken();
        ConfigurationOperations.OperationView apply = operations.createApply(previewId, approvalToken);
        ConfigurationTask applyTask = operations.claim("proxy-a");
        assertEquals("APPLY", applyTask.type());
        assertEquals("a".repeat(64), applyTask.expectedRevision());
        assertThrows(ValidationException.class,
                () -> operations.createApply(previewId, approvalToken));
        assertEquals(apply.operationId(), applyTask.operationId());
    }

    @Test void auditChainSurvivesRestartAndTamperingFailsClosed() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        ConfigurationAuditLog first = new ConfigurationAuditLog(directory, clock);
        first.append("TEST", UUID.randomUUID(), "proxy-a", "OK");
        ConfigurationAuditLog reopened = new ConfigurationAuditLog(directory, clock);
        reopened.append("TEST", UUID.randomUUID(), null, "OK");
        Path file = directory.resolve("configuration-audit.jsonl");
        Files.writeString(file, Files.readString(file).replaceFirst("TEST", "EDIT"));
        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory, clock));
    }

    @Test void abandonedOperationsExpireAndCannotConsumeTheBoundForever() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
        registry.register(new NodeRegistration("proxy-a", UUID.randomUUID(), "Proxy A", "VELOCITY", "test", 1,
                Set.of("presence.snapshot", ConfigurationOperations.CAPABILITY), Set.of("presence.snapshot")));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        UUID abandoned = operations.createRead(List.of("proxy-a")).operationId();

        clock.advance(Duration.ofMinutes(16));

        assertEquals("OPERATION_NOT_FOUND",
                assertThrows(ValidationException.class, () -> operations.get(abandoned)).code());
    }

    @Test void retainedAuditSegmentIsVerifiedBeforeStartup() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        Path active = directory.resolve("configuration-audit.jsonl");
        Path retained = directory.resolve("configuration-audit.jsonl.1");
        ConfigurationAuditLog first = new ConfigurationAuditLog(directory, clock);
        first.append("TEST", UUID.randomUUID(), "proxy-a", "OK");
        Files.move(active, retained);
        ConfigurationAuditLog second = new ConfigurationAuditLog(directory, clock);
        second.append("TEST", UUID.randomUUID(), "proxy-b", "OK");
        Files.writeString(retained, Files.readString(retained).replaceFirst("TEST", "EDIT"));

        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory, clock));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
