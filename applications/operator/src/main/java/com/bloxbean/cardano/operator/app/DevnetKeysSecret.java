package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@KubernetesDependent
public class DevnetKeysSecret extends CRUDKubernetesDependentResource<Secret, CardanoNode> {

    public DevnetKeysSecret() {
        super(Secret.class);
    }

    @Override
    protected Secret desired(CardanoNode primary, Context<CardanoNode> context) {
        String name = secretName(primary);

        return new SecretBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels("app", "cardano-node")
                .addToLabels("cardano-node-resource", primary.getMetadata().getName())
                .endMetadata()
                .withType("Opaque")
                .withStringData(Map.of(
                        "kes.skey", readResource("devnet/pool-keys/kes.skey"),
                        "vrf.skey", readResource("devnet/pool-keys/vrf.skey"),
                        "byron-delegation.cert", readResource("devnet/pool-keys/byron-delegation.cert"),
                        "byron-delegate.key", readResource("devnet/pool-keys/byron-delegate.key"),
                        "opcert.cert", readResource("devnet/pool-keys/opcert.cert")
                ))
                .build();
    }

    private String secretName(CardanoNode cr) {
        if (cr.getSpec() != null && cr.getSpec().getDevnetKeysSecret() != null
                && !cr.getSpec().getDevnetKeysSecret().isBlank()) {
            return cr.getSpec().getDevnetKeysSecret();
        }
        return cr.getMetadata().getName() + "-devnet-keys";
    }

    private String readResource(String path) {
        String fullPath = "com/bloxbean/cardano/operator/app/" + path;
        try (InputStream is = DevnetKeysSecret.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + fullPath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + fullPath, e);
        }
    }
}
