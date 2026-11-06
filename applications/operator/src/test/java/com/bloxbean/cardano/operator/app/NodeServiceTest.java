package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NodeServiceTest {

    @Test
    void selectorIsScopedToTheCardanoNodeResource() {
        NodeService nodeService = new NodeService();

        CardanoNode cardanoNode = new CardanoNode();
        cardanoNode.setMetadata(new ObjectMetaBuilder()
                .withName("my-cardano-node")
                .withNamespace("default")
                .build());

        Service svc = nodeService.desired(cardanoNode, null);

        assertNotNull(svc);
        assertEquals("my-cardano-node", svc.getMetadata().getName());
        // The selector must include the per-CR label, otherwise two CardanoNode CRs
        // in the same namespace would select each other's pods.
        assertEquals("my-cardano-node", svc.getSpec().getSelector().get("cardano-node-resource"));
        assertEquals("cardano-node", svc.getSpec().getSelector().get("app"));
    }
}
