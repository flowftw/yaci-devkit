package com.bloxbean.cardano.operator.app;

public class CardanoNodeStatus {

    private String phase;
    private String message;
    private String deploymentName;
    private Integer replicas;
    private Integer readyReplicas;
    private Long observedGeneration;
    private String lastReconciledAt;

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDeploymentName() {
        return deploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    public Integer getReadyReplicas() {
        return readyReplicas;
    }

    public void setReadyReplicas(Integer readyReplicas) {
        this.readyReplicas = readyReplicas;
    }

    public Long getObservedGeneration() {
        return observedGeneration;
    }

    public void setObservedGeneration(Long observedGeneration) {
        this.observedGeneration = observedGeneration;
    }

    public String getLastReconciledAt() {
        return lastReconciledAt;
    }

    public void setLastReconciledAt(String lastReconciledAt) {
        this.lastReconciledAt = lastReconciledAt;
    }
}
