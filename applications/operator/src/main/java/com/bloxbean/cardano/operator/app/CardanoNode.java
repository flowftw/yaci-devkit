package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("com.bloxbean")
@Version("v1")
public class CardanoNode extends CustomResource<CardanoNodeSpec, CardanoNodeStatus> implements Namespaced {
    
}
