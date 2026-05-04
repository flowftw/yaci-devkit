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
}
