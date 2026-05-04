package com.bloxbean.cardano.operator.app;

public class CardanoNodeSpec {

    private String image;
    private Integer replicas;
    private String network;

    // Used when network=devnet
    private String devnetConfigMap;
    private String devnetKeysSecret;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getDevnetConfigMap() {
        return devnetConfigMap;
    }

    public void setDevnetConfigMap(String devnetConfigMap) {
        this.devnetConfigMap = devnetConfigMap;
    }

    public String getDevnetKeysSecret() {
        return devnetKeysSecret;
    }

    public void setDevnetKeysSecret(String devnetKeysSecret) {
        this.devnetKeysSecret = devnetKeysSecret;
    }
}
