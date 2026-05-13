package com.bloxbean.cardano.operator.app;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class CardanoNodeStatus {

    private String phase;
    private String message;
    private String deploymentName;
    private Integer replicas;
    private Integer readyReplicas;
    private Long observedGeneration;

    @EqualsAndHashCode.Exclude
    private String lastReconciledAt;

    // Yaci Store and UI status
    private String storePhase;
    private String storeMessage;
    private Integer storeReadyReplicas;

    private String storeUiPhase;
    private String storeUiMessage;
    private Integer storeUiReadyReplicas;
}
