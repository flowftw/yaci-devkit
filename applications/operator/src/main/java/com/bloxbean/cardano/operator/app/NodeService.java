package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.HashMap;
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

        var metadata = primary.getMetadata();
        String resourceName = metadata.getName();
        String namespace = metadata.getNamespace();

        svc.getMetadata().setName(resourceName);
        svc.getMetadata().setNamespace(namespace);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "cardano-node");
        labels.put("cardano-node-resource", resourceName);
        labels.put("app.kubernetes.io/managed-by", "java-operator-sdk");
        svc.getMetadata().setLabels(labels);

        if (metadata.getUid() != null) {
            OwnerReference ownerReference = new OwnerReferenceBuilder()
                    .withApiVersion(primary.getApiVersion())
                    .withKind(primary.getKind())
                    .withName(resourceName)
                    .withUid(metadata.getUid())
                    .withController(true)
                    .withBlockOwnerDeletion(true)
                    .build();
            svc.getMetadata().setOwnerReferences(java.util.Collections.singletonList(ownerReference));
        }

        return svc;
    }
}
