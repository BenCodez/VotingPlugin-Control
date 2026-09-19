package com.bencodez.votingplugin.control.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionQueryTest {
    @Test void supportsOnlyTheBoundedReadOnlyInspectionCatalog() {
        for (String kind : InspectionQuery.KINDS) {
            InspectionQuery query = new InspectionQuery(kind,
                    "reward-file-inventory".equals(kind) ? Map.of() : Map.of("player", "Example"));
            assertEquals(kind, query.kind());
            if (!query.requiresRewardFiles()) assertEquals("Example", query.filters().get("player"));
        }
        assertEquals("data.inspect.v1", InspectionQuery.CAPABILITY);
        assertThrows(IllegalArgumentException.class, () -> new InspectionQuery("raw-sql", Map.of()));
    }

    @Test void rewardFileInventoryHasNoFiltersAndRequiresItsVersionedCapability() {
        InspectionQuery inventory = new InspectionQuery("reward-file-inventory", Map.of());
        assertTrue(inventory.requiresRewardFiles());
        assertEquals("config.reward-files.v1", InspectionQuery.REWARD_FILE_CAPABILITY);
        assertThrows(IllegalArgumentException.class, () -> new InspectionQuery("reward-file-inventory",
                Map.of("name", "StandardVote")));
    }

    @Test void normalizesNullFiltersAndDefensivelyCopiesThem() {
        assertTrue(new InspectionQuery("overview", null).filters().isEmpty());
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("player", "Example");
        InspectionQuery query = new InspectionQuery("player", filters);
        filters.clear();

        assertEquals(Map.of("player", "Example"), query.filters());
        assertThrows(UnsupportedOperationException.class, () -> query.filters().put("server", "lobby"));
    }

    @Test void rejectsExcessiveOrMalformedFilters() {
        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int index = 0; index < 13; index++) tooMany.put("filter" + index, "value");

        assertThrows(IllegalArgumentException.class, () -> new InspectionQuery("overview", tooMany));
        assertThrows(IllegalArgumentException.class,
                () -> new InspectionQuery("overview", Map.of("Bad-key", "value")));
        assertThrows(IllegalArgumentException.class,
                () -> new InspectionQuery("overview", Map.of("filter", "x".repeat(501))));
        assertEquals("é".repeat(250), new InspectionQuery("overview", Map.of("filter", "é".repeat(250)))
                .filters().get("filter"));
        assertThrows(IllegalArgumentException.class,
                () -> new InspectionQuery("overview", Map.of("filter", "é".repeat(251))));
        assertThrows(IllegalArgumentException.class,
                () -> new InspectionQuery("overview", Map.of("filter", "before\0after")));
    }

    @Test void rewardSimulationAllowsOnlyItsProposalFilterToUseTheLargerBound() {
        String maximumProposal = "x".repeat(InspectionQuery.MAX_REWARD_PROPOSAL);

        assertEquals(maximumProposal, new InspectionQuery("reward-simulation",
                Map.of("proposal", maximumProposal)).filters().get("proposal"));
        assertThrows(IllegalArgumentException.class, () -> new InspectionQuery("reward-simulation",
                Map.of("proposal", maximumProposal + "x")));
        assertThrows(IllegalArgumentException.class, () -> new InspectionQuery("reward-simulation",
                Map.of("player", "x".repeat(501))));
        assertThrows(IllegalArgumentException.class, () -> new InspectionQuery("overview",
                Map.of("proposal", "x".repeat(501))));
    }
}
