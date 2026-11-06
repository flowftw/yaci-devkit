package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class NodeStatefulSet extends CRUDKubernetesDependentResource<StatefulSet, CardanoNode> {

    private static final String DEFAULT_IMAGE = "blinklabs/cardano-node:10.5.1";
    private static final String DEFAULT_NETWORK = "mainnet";
    private static final int DEFAULT_REPLICAS = 1;
    private static final String DEFAULT_STORAGE = "10Gi";

    public NodeStatefulSet() {
        super(StatefulSet.class);
    }

    @Override
    protected StatefulSet desired(CardanoNode cardanoNode, Context<CardanoNode> context) {
        StatefulSet statefulSet = ReconcilerUtilsInternal.loadYaml(StatefulSet.class, NodeStatefulSet.class, "statefulset.yaml");

        var metadata = cardanoNode.getMetadata();
        var spec = cardanoNode.getSpec();

        String resourceName = metadata.getName();
        String network = spec != null && spec.getNetwork() != null ? spec.getNetwork() : DEFAULT_NETWORK;
        boolean devnet = isDevnet(network);

        String image = (spec != null && spec.getImage() != null) ? spec.getImage() : DEFAULT_IMAGE;
        int replicas = spec != null && spec.getReplicas() != null ? spec.getReplicas() : DEFAULT_REPLICAS;
        String storage = (spec != null && spec.getStorage() != null) ? spec.getStorage() : DEFAULT_STORAGE;

        Map<String, String> labels = KubernetesResourceHelpers.labels("cardano-node", resourceName);
        KubernetesResourceHelpers.applyMetadata(statefulSet, cardanoNode, resourceName, labels);

        if (statefulSet.getSpec() != null) {
            statefulSet.getSpec().setReplicas(replicas);
            statefulSet.getSpec().setServiceName(resourceName);

            if (statefulSet.getSpec().getSelector() != null) {
                statefulSet.getSpec().getSelector().setMatchLabels(labels);
            }

            if (statefulSet.getSpec().getTemplate() != null
                    && statefulSet.getSpec().getTemplate().getMetadata() != null) {
                statefulSet.getSpec().getTemplate().getMetadata().setLabels(labels);
            }

            // Set PVC storage size
            if (statefulSet.getSpec().getVolumeClaimTemplates() != null
                    && !statefulSet.getSpec().getVolumeClaimTemplates().isEmpty()) {
                var pvc = statefulSet.getSpec().getVolumeClaimTemplates().get(0);
                if (pvc.getSpec() != null && pvc.getSpec().getResources() != null
                        && pvc.getSpec().getResources().getRequests() != null) {
                    pvc.getSpec().getResources().getRequests().put("storage", new Quantity(storage));
                }
            }

            if (statefulSet.getSpec().getTemplate() != null
                    && statefulSet.getSpec().getTemplate().getSpec() != null
                    && statefulSet.getSpec().getTemplate().getSpec().getContainers() != null
                    && !statefulSet.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {

                PodSpec podSpec = statefulSet.getSpec().getTemplate().getSpec();
                Container container = podSpec.getContainers().get(0);
                container.setImage(image);

                if (devnet) {
                    configureDevnetContainer(cardanoNode, spec, podSpec, container);
                } else {
                    KubernetesResourceHelpers.upsertEnvVar(container.getEnv(), "NETWORK", network, container::setEnv);
                }
            }
        }

        return statefulSet;
    }

    private void configureDevnetContainer(CardanoNode cr, CardanoNodeSpec spec, PodSpec podSpec, Container container) {
        String baseName = cr.getMetadata().getName();
        String configMapName = spec != null && spec.getDevnetConfigMap() != null
                ? spec.getDevnetConfigMap()
                : baseName + "-devnet-config";
        String keysSecretName = spec != null && spec.getDevnetKeysSecret() != null
                ? spec.getDevnetKeysSecret()
                : baseName + "-devnet-keys";

        // Merge devnet volumes into whatever the template already declared, instead
        // of replacing the whole list, so volumes added to the template later are
        // not silently dropped.
        Map<String, Volume> volumesByName = new LinkedHashMap<>();
        if (podSpec.getVolumes() != null) {
            podSpec.getVolumes().forEach(v -> volumesByName.put(v.getName(), v));
        }
        volumesByName.put("node-ipc",
                new VolumeBuilder().withName("node-ipc").withNewEmptyDir().endEmptyDir().build());
        volumesByName.put("devnet-config",
                new VolumeBuilder().withName("devnet-config").withNewConfigMap().withName(configMapName).endConfigMap().build());
        volumesByName.put("devnet-keys",
                new VolumeBuilder().withName("devnet-keys").withNewSecret().withSecretName(keysSecretName).withDefaultMode(256).endSecret().build());
        podSpec.setVolumes(new ArrayList<>(volumesByName.values()));

        container.setVolumeMounts(List.of(
                new VolumeMountBuilder().withName("node-data").withMountPath("/data").build(),
                new VolumeMountBuilder().withName("node-ipc").withMountPath("/ipc").build(),
                new VolumeMountBuilder().withName("devnet-config").withMountPath("/etc/cardano/devnet").withReadOnly(true).build(),
                new VolumeMountBuilder().withName("devnet-keys").withMountPath("/etc/cardano/devnet/pool-keys").withReadOnly(true).build()
        ));

        container.setCommand(List.of("cardano-node"));
        container.setArgs(List.of(
                "run",
                "--config", "/etc/cardano/devnet/configuration.json",
                "--topology", "/etc/cardano/devnet/topology.json",
                "--database-path", "/data/db",
                "--socket-path", "/ipc/node.sock",
                "--shelley-kes-key", "/etc/cardano/devnet/pool-keys/kes.skey",
                "--shelley-vrf-key", "/etc/cardano/devnet/pool-keys/vrf.skey",
                "--byron-delegation-certificate", "/etc/cardano/devnet/pool-keys/byron-delegation.cert",
                "--byron-signing-key", "/etc/cardano/devnet/pool-keys/byron-delegate.key",
                "--shelley-operational-certificate", "/etc/cardano/devnet/pool-keys/opcert.cert",
                "--port", "3001"
        ));

        container.setPorts(List.of(
                new ContainerPortBuilder().withName("node-port").withContainerPort(3001).build(),
                new ContainerPortBuilder().withName("prometheus").withContainerPort(12788).build()
        ));

        KubernetesResourceHelpers.upsertEnvVar(container.getEnv(), "CARDANO_NODE_SOCKET_PATH", "/ipc/node.sock", container::setEnv);
        KubernetesResourceHelpers.upsertEnvVar(container.getEnv(), "NETWORK", "devnet", container::setEnv);
    }

    static boolean isDevnet(String network) {
        return "devnet".equalsIgnoreCase(network) || "local-devnet".equalsIgnoreCase(network);
    }
}
