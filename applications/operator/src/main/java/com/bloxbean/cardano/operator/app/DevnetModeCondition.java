package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DevnetModeCondition implements Condition<HasMetadata, CardanoNode> {

    @Override
    public boolean isMet(DependentResource<HasMetadata, CardanoNode> dependentResource,
                         CardanoNode primary,
                         Context<CardanoNode> context) {
        if (primary.getSpec() == null || primary.getSpec().getNetwork() == null) {
            return false;
        }

        String network = primary.getSpec().getNetwork();
        return "devnet".equalsIgnoreCase(network) || "local-devnet".equalsIgnoreCase(network);
    }
}
