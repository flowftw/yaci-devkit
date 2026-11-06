package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

@KubernetesDependent
public class NodeService extends CRUDKubernetesDependentResource<Service, CardanoNode> {

    public NodeService() {
        super(Service.class);
    }

    @Override
    protected Service desired(CardanoNode primary, Context<CardanoNode> context) {
        Service svc = ReconcilerUtilsInternal.loadYaml(
                Service.class, NodeService.class, "node-service.yaml");

        String resourceName = primary.getMetadata().getName();
        Map<String, String> labels = KubernetesResourceHelpers.labels("cardano-node", resourceName);
        KubernetesResourceHelpers.applyMetadata(svc, primary, resourceName, labels);

        // Scope the selector to this CR's pods. The template ships with a generic
        // "app: cardano-node" selector; without overriding it, two CardanoNode CRs in
        // the same namespace would each select the union of all node pods.
        if (svc.getSpec() != null) {
            svc.getSpec().setSelector(labels);
        }

        return svc;
    }
}
