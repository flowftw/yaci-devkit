package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
                        "kes.skey", readTextResource("devnet/pool-keys/kes.skey"),
                        "vrf.skey", readTextResource("devnet/pool-keys/vrf.skey"),
                        "byron-delegation.cert", readTextResource("devnet/pool-keys/byron-delegation.cert"),
                        "opcert.cert", readTextResource("devnet/pool-keys/opcert.cert")
                ))
                .withData(Map.of(
                        "byron-delegate.key", readBinaryBase64("devnet/pool-keys/byron-delegate.key")
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

    private String readTextResource(String path) {
        try (InputStream is = DevnetKeysSecret.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    private String readBinaryBase64(String path) {
        try (InputStream is = DevnetKeysSecret.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return Base64.getEncoder().encodeToString(is.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }
}
