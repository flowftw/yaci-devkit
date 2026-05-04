package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
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

        String network = spec != null && spec.getNetwork() != null ? spec.getNetwork() : DEFAULT_NETWORK;
        boolean devnet = "devnet".equalsIgnoreCase(network) || "local-devnet".equalsIgnoreCase(network);

        String image = (spec != null && spec.getImage() != null) ? spec.getImage() : DEFAULT_IMAGE;
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

                PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
                Container container = podSpec.getContainers().get(0);
                container.setImage(image);

                if (devnet) {
                    configureDevnetContainer(cardanoNode, spec, podSpec, container);
                } else {
                    upsertEnvVar(container.getEnv(), "NETWORK", network, container::setEnv);
                }
            }
        }

        return deployment;
    }

    private void configureDevnetContainer(CardanoNode cr, CardanoNodeSpec spec, PodSpec podSpec, Container container) {
        String baseName = cr.getMetadata().getName();
        String configMapName = spec != null && spec.getDevnetConfigMap() != null
                ? spec.getDevnetConfigMap()
                : baseName + "-devnet-config";
        String keysSecretName = spec != null && spec.getDevnetKeysSecret() != null
                ? spec.getDevnetKeysSecret()
                : baseName + "-devnet-keys";

        podSpec.setVolumes(List.of(
                new VolumeBuilder().withName("node-data").withNewEmptyDir().endEmptyDir().build(),
                new VolumeBuilder().withName("node-ipc").withNewEmptyDir().endEmptyDir().build(),
                new VolumeBuilder().withName("devnet-config").withNewConfigMap().withName(configMapName).endConfigMap().build(),
                new VolumeBuilder().withName("devnet-keys").withNewSecret().withSecretName(keysSecretName).withDefaultMode(256).endSecret().build()
        ));

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

        upsertEnvVar(container.getEnv(), "CARDANO_NODE_SOCKET_PATH", "/ipc/node.sock", container::setEnv);
        upsertEnvVar(container.getEnv(), "NETWORK", "devnet", container::setEnv);
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
