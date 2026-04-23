package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.HashMap;
import java.util.Map;

@KubernetesDependent
public class NodeDeployment extends CRUDKubernetesDependentResource<Deployment, CardanoNode> {

    public NodeDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(CardanoNode cardanoNode, Context<CardanoNode> context) {
        Deployment deployment = ReconcilerUtils.loadYaml(Deployment.class, NodeDeployment.class, "deployment.yaml");
        
        // Set metadata
        deployment.getMetadata().setName(cardanoNode.getMetadata().getName());
        deployment.getMetadata().setNamespace(cardanoNode.getMetadata().getNamespace());
        
        // Set labels
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "cardano-node");
        labels.put("cardano-node-resource", cardanoNode.getMetadata().getName());
        deployment.getMetadata().setLabels(labels);
        
        // Set owner reference so the deployment is managed by the CardanoNode CR
        OwnerReference ownerReference = new OwnerReferenceBuilder()
                .withApiVersion(cardanoNode.getApiVersion())
                .withKind(cardanoNode.getKind())
                .withName(cardanoNode.getMetadata().getName())
                .withUid(cardanoNode.getMetadata().getUid())
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();
        deployment.getMetadata().setOwnerReferences(java.util.Collections.singletonList(ownerReference));
        
        // Configure the pod template labels and selector
        if (deployment.getSpec() != null) {
            if (deployment.getSpec().getSelector() != null) {
                deployment.getSpec().getSelector().setMatchLabels(labels);
            }
            if (deployment.getSpec().getTemplate() != null && deployment.getSpec().getTemplate().getMetadata() != null) {
                deployment.getSpec().getTemplate().getMetadata().setLabels(labels);
            }
        }
        
        return deployment;
    }
}
