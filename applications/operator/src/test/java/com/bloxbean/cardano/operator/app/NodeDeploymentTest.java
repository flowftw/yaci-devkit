package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NodeDeploymentTest {

    @Test
    void testDesiredReturnsDeploymentWithCorrectName() {
        NodeDeployment nodeDeployment = new NodeDeployment();

        Deployment deployment = nodeDeployment.desired(null, null);

        assertNotNull(deployment, "Deployment should not be null");
        assertNotNull(deployment.getMetadata(), "Deployment metadata should not be null");
        assertEquals("nginx", deployment.getMetadata().getName());
    }
}
