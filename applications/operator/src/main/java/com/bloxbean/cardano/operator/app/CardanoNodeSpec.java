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
}
