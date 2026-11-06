package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

@KubernetesDependent
public class YaciStoreService extends CRUDKubernetesDependentResource<Service, CardanoNode> {

    public YaciStoreService() {
        super(Service.class);
    }

    @Override
    protected Service desired(CardanoNode primary, Context<CardanoNode> context) {
        Service svc = ReconcilerUtilsInternal.loadYaml(
                Service.class, YaciStoreService.class, "yaci-store-service.yaml");

        String resourceName = primary.getMetadata().getName();
        Map<String, String> labels = KubernetesResourceHelpers.labels("yaci-store", resourceName);
        KubernetesResourceHelpers.applyMetadata(svc, primary, resourceName + "-store", labels);

        // Scope the selector to this CR's store pods (see NodeService for rationale).
        if (svc.getSpec() != null) {
            svc.getSpec().setSelector(labels);
        }

        return svc;
    }
}
