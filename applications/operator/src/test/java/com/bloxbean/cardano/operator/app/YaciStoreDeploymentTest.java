package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YaciStoreDeploymentTest {

    @Test
    void storeDataVolumeMountsThePvcAndPointsAtNodeService() {
        YaciStoreDeployment storeDeployment = new YaciStoreDeployment();

        CardanoNodeSpec spec = new CardanoNodeSpec();
        spec.setNetwork("devnet");
        spec.setYaciStoreEnabled(true);
        spec.setDevnetConfigMap("devnet-cm");

        CardanoNode cardanoNode = new CardanoNode();
        cardanoNode.setSpec(spec);
        cardanoNode.setMetadata(new ObjectMetaBuilder()
                .withName("devnet-node")
                .withNamespace("default")
                .build());

        Deployment deployment = storeDeployment.desired(cardanoNode, null);

        assertNotNull(deployment);
        assertEquals("devnet-node-store", deployment.getMetadata().getName());

        var podSpec = deployment.getSpec().getTemplate().getSpec();

        // store-data must reference the PVC provisioned by YaciStorePvc (not emptyDir)
        var storeDataVolume = podSpec.getVolumes().stream()
                .filter(v -> "store-data".equals(v.getName()))
                .findFirst()
                .orElseThrow();
        assertNotNull(storeDataVolume.getPersistentVolumeClaim());
        assertEquals("devnet-node-store-data", storeDataVolume.getPersistentVolumeClaim().getClaimName());

        // devnet-config volume must point at the spec's ConfigMap
        var devnetConfigVolume = podSpec.getVolumes().stream()
                .filter(v -> "devnet-config".equals(v.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("devnet-cm", devnetConfigVolume.getConfigMap().getName());

        // STORE_CARDANO_HOST must point at the node StatefulSet headless service
        var container = podSpec.getContainers().get(0);
        String host = container.getEnv().stream()
                .filter(e -> "STORE_CARDANO_HOST".equals(e.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();
        assertEquals("devnet-node", host);

        // Selector must be scoped to this CR
        assertEquals("devnet-node", deployment.getSpec().getSelector().getMatchLabels().get("cardano-node-resource"));
        assertTrue(deployment.getSpec().getTemplate().getMetadata().getLabels().containsKey("cardano-node-resource"));
    }
}
