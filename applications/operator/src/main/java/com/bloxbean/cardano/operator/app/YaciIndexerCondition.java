package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * Only reconcile Yaci Indexer and UI resources when the feature flag
 * is enabled and the network is set to devnet.
 */
public class YaciIndexerCondition implements Condition<HasMetadata, CardanoNode> {

    @Override
    public boolean isMet(DependentResource<HasMetadata, CardanoNode> dependentResource,
                         CardanoNode primary,
                         Context<CardanoNode> context) {
        if (primary.getSpec() == null) {
            return false;
        }

        // Feature flag must be explicitly enabled
        if (primary.getSpec().getYaciIndexerEnabled() == null
                || !primary.getSpec().getYaciIndexerEnabled()) {
            return false;
        }

        // Indexer only supports devnet mode
        String network = primary.getSpec().getNetwork();
        return "devnet".equalsIgnoreCase(network) || "local-devnet".equalsIgnoreCase(network);
    }
}
