package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

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

        Map<String, String> labels = KubernetesResourceHelpers.labels("yaci-store-ui", resourceName);
        KubernetesResourceHelpers.applyMetadata(deployment, primary, uiName, labels);

        if (deployment.getSpec() != null && deployment.getSpec().getSelector() != null) {
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

            KubernetesResourceHelpers.upsertEnvVar(container.getEnv(), "PUBLIC_STORE_BASE_URL", storeBaseUrl, container::setEnv);
            KubernetesResourceHelpers.upsertEnvVar(container.getEnv(), "PUBLIC_STORE_WS_URL", storeWsUrl, container::setEnv);
        }

        return deployment;
    }
}
