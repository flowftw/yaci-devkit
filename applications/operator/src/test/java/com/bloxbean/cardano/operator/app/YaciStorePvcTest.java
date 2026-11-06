package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YaciStorePvcTest {

    @Test
    void pvcUsesDerivedNameAndDefaultStorage() {
        YaciStorePvc pvcResource = new YaciStorePvc();

        CardanoNode cardanoNode = new CardanoNode();
        cardanoNode.setMetadata(new ObjectMetaBuilder()
                .withName("devnet-node")
                .withNamespace("default")
                .build());

        PersistentVolumeClaim pvc = pvcResource.desired(cardanoNode, null);

        assertNotNull(pvc);
        assertEquals("devnet-node-store-data", pvc.getMetadata().getName());
        assertEquals("default", pvc.getMetadata().getNamespace());
        assertEquals("10Gi", pvc.getSpec().getResources().getRequests().get("storage").toString());
        assertEquals("ReadWriteOnce", pvc.getSpec().getAccessModes().get(0));
        assertEquals("devnet-node", pvc.getMetadata().getLabels().get("cardano-node-resource"));
    }

    @Test
    void pvcHonoursSpecStorageSize() {
        YaciStorePvc pvcResource = new YaciStorePvc();

        CardanoNodeSpec spec = new CardanoNodeSpec();
        spec.setYaciStoreStorage("50Gi");

        CardanoNode cardanoNode = new CardanoNode();
        cardanoNode.setSpec(spec);
        cardanoNode.setMetadata(new ObjectMetaBuilder()
                .withName("devnet-node")
                .withNamespace("default")
                .build());

        PersistentVolumeClaim pvc = pvcResource.desired(cardanoNode, null);

        assertEquals("50Gi", pvc.getSpec().getResources().getRequests().get("storage").toString());
    }
}
