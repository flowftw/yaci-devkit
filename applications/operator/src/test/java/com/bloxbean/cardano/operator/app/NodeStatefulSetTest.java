package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeStatefulSetTest {

    @Test
    void testDesiredReturnsStatefulSetWithCorrectName() {
        NodeStatefulSet nodeStatefulSet = new NodeStatefulSet();

        CardanoNodeSpec spec = new CardanoNodeSpec();
        spec.setImage("ghcr.io/example/cardano-node:v1");
        spec.setReplicas(2);
        spec.setNetwork("preprod");
        spec.setStorage("20Gi");

        CardanoNode cardanoNode = new CardanoNode();
        cardanoNode.setSpec(spec);
        cardanoNode.setMetadata(new ObjectMetaBuilder()
                .withName("my-cardano-node")
                .withNamespace("default")
                .build());

        StatefulSet statefulSet = nodeStatefulSet.desired(cardanoNode, null);

        assertNotNull(statefulSet, "StatefulSet should not be null");
        assertNotNull(statefulSet.getMetadata(), "StatefulSet metadata should not be null");
        assertEquals("my-cardano-node", statefulSet.getMetadata().getName());
        assertEquals(2, statefulSet.getSpec().getReplicas());
        assertEquals("ghcr.io/example/cardano-node:v1",
                statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        assertEquals("preprod", statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().stream()
                .filter(e -> "NETWORK".equals(e.getName()))
                .findFirst()
                .orElseThrow()
                .getValue());
        assertEquals("my-cardano-node", statefulSet.getSpec().getServiceName());

        // Verify PVC template
        assertEquals(1, statefulSet.getSpec().getVolumeClaimTemplates().size());
        var pvc = statefulSet.getSpec().getVolumeClaimTemplates().get(0);
        assertEquals("node-data", pvc.getMetadata().getName());
        assertEquals("20Gi",
                pvc.getSpec().getResources().getRequests().get("storage").toString());
    }

    @Test
    void testDevnetModeUsesCardanoNodeImageAndDevnetCommand() {
        NodeStatefulSet nodeStatefulSet = new NodeStatefulSet();

        CardanoNodeSpec spec = new CardanoNodeSpec();
        spec.setNetwork("devnet");
        spec.setDevnetConfigMap("devnet-config");
        spec.setDevnetKeysSecret("devnet-keys");

        CardanoNode cardanoNode = new CardanoNode();
        cardanoNode.setSpec(spec);
        cardanoNode.setMetadata(new ObjectMetaBuilder()
                .withName("devnet-node")
                .withNamespace("default")
                .build());

        StatefulSet statefulSet = nodeStatefulSet.desired(cardanoNode, null);
        var container = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0);

        assertEquals("blinklabs/cardano-node:10.5.1", container.getImage());
        assertEquals("cardano-node", container.getCommand().get(0));
        assertTrue(container.getArgs().contains("--config"));
        assertTrue(container.getArgs().contains("/etc/cardano/devnet/configuration.json"));
        assertTrue(container.getPorts().stream().anyMatch(p -> p.getContainerPort() == 3001));

        // ConfigMap volume is in pod spec, PVC is in volumeClaimTemplates
        assertTrue(statefulSet.getSpec().getTemplate().getSpec().getVolumes().stream()
                .anyMatch(v -> "devnet-config".equals(v.getName())));
        // node-data should NOT be in pod volumes — it comes from PVC template
        assertTrue(statefulSet.getSpec().getTemplate().getSpec().getVolumes().stream()
                .noneMatch(v -> "node-data".equals(v.getName())));
        // node-data should be a volumeClaimTemplate
        assertTrue(statefulSet.getSpec().getVolumeClaimTemplates().stream()
                .anyMatch(v -> "node-data".equals(v.getMetadata().getName())));
    }
}
