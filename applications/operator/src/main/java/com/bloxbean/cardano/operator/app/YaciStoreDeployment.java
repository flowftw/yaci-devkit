package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

@KubernetesDependent
public class YaciStoreDeployment extends CRUDKubernetesDependentResource<Deployment, CardanoNode> {

    private static final String DEFAULT_STORE_IMAGE = "bloxbean/yaci-store:2.0.0";

    public YaciStoreDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(CardanoNode primary, Context<CardanoNode> context) {
        Deployment deployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, YaciStoreDeployment.class, "yaci-store-deployment.yaml");

        var metadata = primary.getMetadata();
        var spec = primary.getSpec();

        String resourceName = metadata.getName();
        String storeName = resourceName + "-store";

        String image = (spec != null && spec.getYaciStoreImage() != null)
                ? spec.getYaciStoreImage()
                : DEFAULT_STORE_IMAGE;

        String nodeSvcName = resourceName; // headless service of the node StatefulSet

        Map<String, String> labels = KubernetesResourceHelpers.labels("yaci-store", resourceName);
        KubernetesResourceHelpers.applyMetadata(deployment, primary, storeName, labels);

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

            // Point the store at the node StatefulSet service
            KubernetesResourceHelpers.upsertEnvVar(container.getEnv(), "STORE_CARDANO_HOST", nodeSvcName, container::setEnv);
        }

        // Point the devnet-config volume at the correct ConfigMap and the store-data
        // volume at the PVC provisioned by YaciStorePvc (honours spec.yaciStoreStorage).
        String configMapName = spec != null && spec.getDevnetConfigMap() != null
                ? spec.getDevnetConfigMap()
                : resourceName + "-devnet-config";
        String pvcName = YaciStorePvc.pvcName(primary);

        if (deployment.getSpec() != null
                && deployment.getSpec().getTemplate() != null
                && deployment.getSpec().getTemplate().getSpec() != null) {
            PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
            if (podSpec.getVolumes() != null) {
                podSpec.getVolumes().stream()
                        .filter(v -> "devnet-config".equals(v.getName()))
                        .findFirst()
                        .ifPresent(v -> v.getConfigMap().setName(configMapName));
                podSpec.getVolumes().stream()
                        .filter(v -> "store-data".equals(v.getName()))
                        .findFirst()
                        .ifPresent(v -> v.getPersistentVolumeClaim().setClaimName(pvcName));
            }
        }

        return deployment;
    }
}
