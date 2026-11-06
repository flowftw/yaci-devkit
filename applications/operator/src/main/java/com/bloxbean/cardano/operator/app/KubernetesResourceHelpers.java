package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared helpers for the Kubernetes dependent resources so that labels,
 * owner references and env-var handling stay consistent across dependents
 * and are not copy-pasted in every class.
 */
public final class KubernetesResourceHelpers {

    public static final String APP_LABEL = "app";
    public static final String RESOURCE_LABEL = "cardano-node-resource";
    public static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
    public static final String MANAGED_BY_VALUE = "java-operator-sdk";

    private KubernetesResourceHelpers() {
    }

    /**
     * Standard label set applied to every dependent resource and its pod template.
     * The {@code cardano-node-resource} label is unique per CR, which is what makes
     * service selectors and pod selection scope to a single CardanoNode instance.
     */
    public static Map<String, String> labels(String app, String resourceName) {
        Map<String, String> labels = new HashMap<>();
        labels.put(APP_LABEL, app);
        labels.put(RESOURCE_LABEL, resourceName);
        labels.put(MANAGED_BY_LABEL, MANAGED_BY_VALUE);
        return labels;
    }

    /**
     * A controller owner reference back to the CardanoNode CR.
     * <p>
     * JOSDK auto-adds an owner reference for {@code CRUDKubernetesDependentResource}
     * dependents (they are {@code GarbageCollected}), but the auto-added reference
     * does not set {@code controller} / {@code blockOwnerDeletion}. Setting it
     * explicitly here is harmless: fabric8's {@code addOwnerReference} skips when an
     * owner reference with the same uid already exists, so this one wins and the
     * desired flags are preserved.
     */
    public static OwnerReference controllerOwnerReference(CardanoNode primary) {
        ObjectMeta metadata = primary.getMetadata();
        return new OwnerReferenceBuilder()
                .withApiVersion(primary.getApiVersion())
                .withKind(primary.getKind())
                .withName(metadata.getName())
                .withUid(metadata.getUid())
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();
    }

    /**
     * Applies name, namespace, the standard labels and (when the primary already
     * has a uid) the controller owner reference to a dependent resource's metadata.
     */
    public static void applyMetadata(HasMetadata resource, CardanoNode primary,
                                     String name, Map<String, String> labels) {
        ObjectMeta metadata = resource.getMetadata();
        metadata.setName(name);
        metadata.setNamespace(primary.getMetadata().getNamespace());
        metadata.setLabels(labels);
        if (primary.getMetadata().getUid() != null) {
            metadata.setOwnerReferences(Collections.singletonList(controllerOwnerReference(primary)));
        }
    }

    /** Inserts or replaces an env var by name, then pushes the list back via the setter. */
    public static void upsertEnvVar(List<EnvVar> envVars, String key, String value,
                                    Consumer<List<EnvVar>> setter) {
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
