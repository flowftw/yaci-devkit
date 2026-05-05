package com.bloxbean.cardano.operator.app;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class YaciIndexerUiService extends CRUDKubernetesDependentResource<Service, CardanoNode> {

    public YaciIndexerUiService() {
        super(Service.class);
    }

    @Override
    protected Service desired(CardanoNode primary, Context<CardanoNode> context) {
        Service svc = ReconcilerUtilsInternal.loadYaml(
                Service.class, YaciIndexerUiService.class, "yaci-indexer-ui-service.yaml");

        var metadata = primary.getMetadata();
        String resourceName = metadata.getName();
        String namespace = metadata.getNamespace();
        String svcName = resourceName + "-indexer-ui";

        svc.getMetadata().setName(svcName);
        svc.getMetadata().setNamespace(namespace);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "yaci-indexer-ui");
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
