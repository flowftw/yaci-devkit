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

    // --- Yaci Store (yaci-store) and Store UI (yaci-viewer) ---

    /**
     * Feature flag to enable/disable the Yaci Store and Store UI.
     * Requires network=devnet to take effect.
     */
    private Boolean yaciStoreEnabled;

    /**
     * Docker image for the Yaci Store (yaci-store).
     * Default: bloxbean/yaci-cli:latest
     */
    private String yaciStoreImage;

    /**
     * Docker image for the Yaci Store UI (yaci-viewer).
     * Default: bloxbean/yaci-viewer:latest
     */
    private String yaciStoreUiImage;

    /**
     * Persistent storage size for the store database (e.g. "10Gi").
     * Default: 10Gi
     */
    private String yaciStoreStorage;
}
