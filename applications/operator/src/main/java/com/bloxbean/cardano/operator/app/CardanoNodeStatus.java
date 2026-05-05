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

    // Yaci Indexer and UI status
    private String indexerPhase;
    private String indexerMessage;
    private Integer indexerReadyReplicas;

    private String indexerUiPhase;
    private String indexerUiMessage;
    private Integer indexerUiReadyReplicas;
}
