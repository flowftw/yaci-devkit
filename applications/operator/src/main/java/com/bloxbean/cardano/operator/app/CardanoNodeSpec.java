package com.bloxbean.cardano.operator.app;

import lombok.Data;

@Data
public class CardanoNodeSpec {

    private String image;
    private Integer replicas;
    private String network;

    // Used when network=devnet
    private String devnetConfigMap;
    private String devnetKeysSecret;

    // Persistent storage size for node data (e.g. "10Gi")
    private String storage;

    // --- Yaci Indexer (yaci-store) and Indexer UI (yaci-viewer) ---

    /**
     * Feature flag to enable/disable the Yaci Indexer and Indexer UI.
     * Requires network=devnet to take effect.
     */
    private Boolean yaciIndexerEnabled;

    /**
     * Docker image for the Yaci Indexer (yaci-store).
     * Default: bloxbean/yaci-cli:latest
     */
    private String yaciIndexerImage;

    /**
     * Docker image for the Yaci Indexer UI (yaci-viewer).
     * Default: bloxbean/yaci-viewer:latest
     */
    private String yaciIndexerUiImage;

    /**
     * Persistent storage size for the indexer database (e.g. "10Gi").
     * Default: 10Gi
     */
    private String yaciIndexerStorage;
}
