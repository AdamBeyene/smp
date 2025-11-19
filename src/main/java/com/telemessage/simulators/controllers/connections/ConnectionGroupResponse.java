package com.telemessage.simulators.controllers.connections;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for grouped connections (connection families)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConnectionGroupResponse {
    /**
     * Type: "single" or "family"
     */
    private String type;

    /**
     * For single connections: the connection data
     * For families: represents the primary/first connection
     */
    private AggregatedConnectionData connection;

    /**
     * For families: all related connections in this family
     */
    private List<AggregatedConnectionData> members;

    /**
     * For families: metadata about the group
     */
    private GroupMetadata metadata;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GroupMetadata {
        /**
         * Base ID (e.g., "379" from both "379" and "37900")
         */
        private String baseId;

        /**
         * Group name (e.g., "CLX USA")
         */
        private String groupName;

        /**
         * Total members in this family
         */
        private int memberCount;

        /**
         * Status summary: "All Bound", "All Unbound", "Mixed"
         */
        private String statusSummary;

        /**
         * Count of bound transmitters
         */
        private int boundTransmitters;

        /**
         * Count of bound receivers
         */
        private int boundReceivers;
    }
}
