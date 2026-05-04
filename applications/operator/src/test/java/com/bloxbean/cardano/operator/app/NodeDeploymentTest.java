package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeDeploymentTest {

    @Test
    void testDesiredReturnsDeploymentWithCorrectName() {
        NodeDeployment nodeDeployment = new NodeDeployment();

        CardanoNodeSpec spec = new CardanoNodeSpec();
        spec.setImage("ghcr.io/example/cardano-node:v1");
        spec.setReplicas(2);
        spec.setNetwork("preprod");

        CardanoNode cardanoNode = new CardanoNode();
        cardanoNode.setSpec(spec);
        cardanoNode.setMetadata(new ObjectMetaBuilder()
                .withName("my-cardano-node")
                .withNamespace("default")
                .build());

        Deployment deployment = nodeDeployment.desired(cardanoNode, null);

        assertNotNull(deployment, "Deployment should not be null");
        assertNotNull(deployment.getMetadata(), "Deployment metadata should not be null");
        assertEquals("my-cardano-node", deployment.getMetadata().getName());
        assertEquals(2, deployment.getSpec().getReplicas());
        assertEquals("ghcr.io/example/cardano-node:v1", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        assertEquals("preprod", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().stream()
                .filter(e -> "NETWORK".equals(e.getName()))
                .findFirst()
                .orElseThrow()
                .getValue());
    }

    @Test
    void testDevnetModeUsesCardanoNodeImageAndDevnetCommand() {
        NodeDeployment nodeDeployment = new NodeDeployment();

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

        Deployment deployment = nodeDeployment.desired(cardanoNode, null);
        var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);

        assertEquals("blinklabs/cardano-node:10.5.0", container.getImage());
        assertEquals("cardano-node", container.getCommand().get(0));
        assertTrue(container.getArgs().contains("--config"));
        assertTrue(container.getArgs().contains("/etc/cardano/devnet/configuration.json"));
        assertTrue(container.getPorts().stream().anyMatch(p -> p.getContainerPort() == 3001));
        assertTrue(deployment.getSpec().getTemplate().getSpec().getVolumes().stream()
                .anyMatch(v -> "devnet-config".equals(v.getName())));
    }
}
