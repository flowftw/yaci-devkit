package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

// @KubernetesDependent()
public class NodeDeployment extends CRUDKubernetesDependentResource<Deployment, CardanoNode> {

    @Override
    protected Deployment desired(CardanoNode cardanoNode, Context<CardanoNode> context) {
        Deployment deployment = ReconcilerUtils.loadYaml(Deployment.class, Utils.class, "deployment.yaml");
        deployment.getMetadata().setName(cardanoNode.getMetadata().getName());
        return deployment;
    }
    
}
