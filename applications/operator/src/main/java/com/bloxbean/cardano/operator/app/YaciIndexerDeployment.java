package com.bloxbean.cardano.operator.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@KubernetesDependent
public class YaciIndexerDeployment extends CRUDKubernetesDependentResource<Deployment, CardanoNode> {

    private static final String DEFAULT_INDEXER_IMAGE = "bloxbean/yaci-cli:latest";

    public YaciIndexerDeployment() {
        super(Deployment.class);
    }

    // @Override
    // protected ResourceID targetSecondaryResourceID(CardanoNode primary, Context<CardanoNode> context) {
    //     // TODO Auto-generated method stub
    //     context.getSecondaryResources(Deployment.class).stream().filter(deployment -> deployment.getMetadata().getName() == primary.getMetadata().getName() + "-");
    //     return super.targetSecondaryResourceID(primary, context);
    // }

    @Override
    protected Deployment desired(CardanoNode primary, Context<CardanoNode> context) {
        Deployment deployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, YaciIndexerDeployment.class, "yaci-indexer-deployment.yaml");

        var metadata = primary.getMetadata();
        var spec = primary.getSpec();

        String resourceName = metadata.getName();
        String namespace = metadata.getNamespace();
        String indexerName = resourceName + "-indexer";

        String image = (spec != null && spec.getYaciIndexerImage() != null)
                ? spec.getYaciIndexerImage()
                : DEFAULT_INDEXER_IMAGE;

        String nodeSvcName = resourceName; // headless service of the node StatefulSet

        deployment.getMetadata().setName(indexerName);
        deployment.getMetadata().setNamespace(namespace);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "yaci-indexer");
        labels.put("cardano-node-resource", resourceName);
        labels.put("app.kubernetes.io/managed-by", "java-operator-sdk");
        deployment.getMetadata().setLabels(labels);

        if (metadata.getUid() != null) {
            OwnerReference ownerReference = new OwnerReferenceBuilder()
                    .withApiVersion(primary.getApiVersion())
                    .withKind(primary.getKind())
                    .withName(resourceName)
                    .withUid(metadata.getUid())
                    .withController(true)
                    .withBlockOwnerDeletion(true)
                    .build();
            deployment.getMetadata().setOwnerReferences(java.util.Collections.singletonList(ownerReference));
        }

        if (deployment.getSpec() != null
                && deployment.getSpec().getSelector() != null) {
            deployment.getSpec().getSelector().setMatchLabels(labels);
        }

        if (deployment.getSpec() != null
                && deployment.getSpec().getTemplate() != null
                && deployment.getSpec().getTemplate().getMetadata() != null) {
            deployment.getSpec().getTemplate().getMetadata().setLabels(labels);
        }

        // Customise container
        if (deployment.getSpec() != null
                && deployment.getSpec().getTemplate() != null
                && deployment.getSpec().getTemplate().getSpec() != null
                && deployment.getSpec().getTemplate().getSpec().getContainers() != null
                && !deployment.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {

            Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
            container.setImage(image);

            // Point the indexer at the node StatefulSet service
            upsertEnvVar(container.getEnv(), "STORE_CARDANO_HOST", nodeSvcName, container::setEnv);
        }

        // Point the devnet-config volume at the correct ConfigMap
        String configMapName = spec != null && spec.getDevnetConfigMap() != null
                ? spec.getDevnetConfigMap()
                : resourceName + "-devnet-config";

        if (deployment.getSpec() != null
                && deployment.getSpec().getTemplate() != null
                && deployment.getSpec().getTemplate().getSpec() != null) {
            PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
            if (podSpec.getVolumes() != null) {
                podSpec.getVolumes().stream()
                        .filter(v -> "devnet-config".equals(v.getName()))
                        .findFirst()
                        .ifPresent(v -> v.getConfigMap().setName(configMapName));
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
