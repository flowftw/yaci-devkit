package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

/**
 * PersistentVolumeClaim for the Yaci Store database.
 * <p>
 * Realises {@code spec.yaciStoreStorage} (default {@code 10Gi}), which was previously
 * declared in the CRD/example but ignored — the store data volume was an ephemeral
 * emptyDir. The {@link YaciStoreDeployment} mounts this PVC on {@code /data}.
 */
@KubernetesDependent
public class YaciStorePvc extends CRUDKubernetesDependentResource<PersistentVolumeClaim, CardanoNode> {

    private static final String DEFAULT_STORAGE = "10Gi";

    public YaciStorePvc() {
        super(PersistentVolumeClaim.class);
    }

    public static String pvcName(CardanoNode primary) {
        return primary.getMetadata().getName() + "-store-data";
    }

    @Override
    protected PersistentVolumeClaim desired(CardanoNode primary, Context<CardanoNode> context) {
        var spec = primary.getSpec();
        String storage = (spec != null && spec.getYaciStoreStorage() != null && !spec.getYaciStoreStorage().isBlank())
                ? spec.getYaciStoreStorage()
                : DEFAULT_STORAGE;

        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName(pvcName(primary))
                    .withNamespace(primary.getMetadata().getNamespace())
                    .withLabels(KubernetesResourceHelpers.labels("yaci-store", primary.getMetadata().getName()))
                .endMetadata()
                .withNewSpec()
                    .withAccessModes("ReadWriteOnce")
                    .withNewResources()
                        .withRequests(Map.of("storage", new Quantity(storage)))
                    .endResources()
                .endSpec()
                .build();
    }
}
