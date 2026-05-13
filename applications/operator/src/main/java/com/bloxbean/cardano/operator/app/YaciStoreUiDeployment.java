package com.bloxbean.cardano.operator.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class YaciStoreUiDeployment extends CRUDKubernetesDependentResource<Deployment, CardanoNode> {

    private static final String DEFAULT_UI_IMAGE = "bloxbean/yaci-viewer:0.10.6";

    public YaciStoreUiDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(CardanoNode primary, Context<CardanoNode> context) {
        Deployment deployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, YaciStoreUiDeployment.class, "yaci-store-ui-deployment.yaml");

        var metadata = primary.getMetadata();
        var spec = primary.getSpec();

        String resourceName = metadata.getName();
        String namespace = metadata.getNamespace();
        String uiName = resourceName + "-store-ui";
        String storeSvcName = resourceName + "-store";

        String image = (spec != null && spec.getYaciStoreUiImage() != null)
                ? spec.getYaciStoreUiImage()
                : DEFAULT_UI_IMAGE;

        deployment.getMetadata().setName(uiName);
        deployment.getMetadata().setNamespace(namespace);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "yaci-store-ui");
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

            // Point the UI at the store service
            String storeBaseUrl = "http://" + storeSvcName + "." + namespace + ".svc.cluster.local:8080/api/v1";
            String storeWsUrl = "ws://" + storeSvcName + "." + namespace + ".svc.cluster.local:8080/ws/liveblocks";

            upsertEnvVar(container.getEnv(), "PUBLIC_STORE_BASE_URL", storeBaseUrl, container::setEnv);
            upsertEnvVar(container.getEnv(), "PUBLIC_STORE_WS_URL", storeWsUrl, container::setEnv);
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
