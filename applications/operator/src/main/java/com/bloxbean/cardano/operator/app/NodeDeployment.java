package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class NodeDeployment extends CRUDKubernetesDependentResource<Deployment, CardanoNode> {

    private static final String DEFAULT_IMAGE = "blinklabs/cardano-node:main";
    private static final String DEFAULT_NETWORK = "mainnet";
    private static final int DEFAULT_REPLICAS = 1;

    public NodeDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(CardanoNode cardanoNode, Context<CardanoNode> context) {
        Deployment deployment = ReconcilerUtils.loadYaml(Deployment.class, NodeDeployment.class, "deployment.yaml");

        var metadata = cardanoNode.getMetadata();
        var spec = cardanoNode.getSpec();

        String resourceName = metadata.getName();
        String namespace = metadata.getNamespace();

        String image = spec != null && spec.getImage() != null ? spec.getImage() : DEFAULT_IMAGE;
        String network = spec != null && spec.getNetwork() != null ? spec.getNetwork() : DEFAULT_NETWORK;
        int replicas = spec != null && spec.getReplicas() != null ? spec.getReplicas() : DEFAULT_REPLICAS;

        deployment.getMetadata().setName(resourceName);
        deployment.getMetadata().setNamespace(namespace);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "cardano-node");
        labels.put("cardano-node-resource", resourceName);
        labels.put("app.kubernetes.io/managed-by", "java-operator-sdk");
        deployment.getMetadata().setLabels(labels);

        if (metadata.getUid() != null) {
            OwnerReference ownerReference = new OwnerReferenceBuilder()
                    .withApiVersion(cardanoNode.getApiVersion())
                    .withKind(cardanoNode.getKind())
                    .withName(resourceName)
                    .withUid(metadata.getUid())
                    .withController(true)
                    .withBlockOwnerDeletion(true)
                    .build();
            deployment.getMetadata().setOwnerReferences(java.util.Collections.singletonList(ownerReference));
        }

        if (deployment.getSpec() != null) {
            deployment.getSpec().setReplicas(replicas);

            if (deployment.getSpec().getSelector() != null) {
                deployment.getSpec().getSelector().setMatchLabels(labels);
            }

            if (deployment.getSpec().getTemplate() != null
                    && deployment.getSpec().getTemplate().getMetadata() != null) {
                deployment.getSpec().getTemplate().getMetadata().setLabels(labels);
            }

            if (deployment.getSpec().getTemplate() != null
                    && deployment.getSpec().getTemplate().getSpec() != null
                    && deployment.getSpec().getTemplate().getSpec().getContainers() != null
                    && !deployment.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {

                var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
                container.setImage(image);
                upsertEnvVar(container.getEnv(), "NETWORK", network, container::setEnv);
            }
        }

        return deployment;
    }

    private void upsertEnvVar(List<EnvVar> envVars, String key, String value,
                              java.util.function.Consumer<List<EnvVar>> setter) {
        List<EnvVar> values = envVars != null ? new ArrayList<>(envVars) : new ArrayList<>();

        boolean found = false;
        for (EnvVar envVar : values) {
            if (key.equals(envVar.getName())) {
                envVar.setValue(value);
                found = true;
                break;
            }
        }

        if (!found) {
            EnvVar envVar = new EnvVar();
            envVar.setName(key);
            envVar.setValue(value);
            values.add(envVar);
        }

        setter.accept(values);
    }
}
