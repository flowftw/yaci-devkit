package com.bloxbean.cardano.operator.app;

import com.bloxbean.cardano.yacicli.genesis.config.GenesisConfig;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@KubernetesDependent
public class DevnetConfigMap extends CRUDKubernetesDependentResource<ConfigMap, CardanoNode> {

    private static final String TEMPLATE_BASE = "com/bloxbean/cardano/operator/app/devnet/genesis-templates";
    private static final Mustache.Compiler COMPILER = Mustache.compiler();

    public DevnetConfigMap() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(CardanoNode primary, Context<CardanoNode> context) {
        String name = configMapName(primary);
        Map<String, Object> values = buildGenesisValues();

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels("app", "cardano-node")
                .addToLabels("cardano-node-resource", primary.getMetadata().getName())
                .endMetadata()
                .withData(Map.of(
                        "configuration.json", render("configuration.json", values),
                        "topology.json", readStaticResource("devnet/topology.json"),
                        "byron-genesis.json", render("byron-genesis.json", values),
                        "shelley-genesis.json", render("shelley-genesis.json", values),
                        "alonzo-genesis.json", render("alonzo-genesis.json", values),
                        "conway-genesis.json", render("conway-genesis.json", values)
                ))
                .build();
    }

    /**
     * Build the Mustache values map using the shared GenesisConfig from genesis-config module,
     * with operator-specific runtime defaults.
     */
    private Map<String, Object> buildGenesisValues() {
        GenesisConfig genesisConfig = new GenesisConfig();
        // Trigger @PostConstruct initialization (normally done by Spring, but operator calls it manually)
        genesisConfig.postInit();

        Map values = genesisConfig.getConfigMap();

        // Add runtime values not in GenesisConfig.getConfigMap()
        values.put("slotLength", 1);
        values.put("activeSlotsCoeff", 0.05);
        values.put("epochLength", 43200);

        // Auto-compute security param if not set
        long securityParam = genesisConfig.getSecurityParam();
        if (securityParam == 0) {
            securityParam = Math.round((43200 * 0.05 * 0.5) / 3.0);
        }
        values.put("securityParam", securityParam);

        // Configuration.json template values
        values.put("enableP2P", true);
        values.put("peerSharing", true);
        values.put("conway_era", true);

        return values;
    }

    private String render(String templateFile, Map<String, Object> values) {
        String fullPath = TEMPLATE_BASE + "/" + templateFile;
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(fullPath),
                        "Missing template: " + fullPath), StandardCharsets.UTF_8)) {
            Template template = COMPILER.compile(reader);
            StringWriter out = new StringWriter();
            template.execute(values, out);
            return out.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render template: " + fullPath, e);
        }
    }

    private String readStaticResource(String path) {
        String fullPath = "com/bloxbean/cardano/operator/app/" + path;
        try (var is = DevnetConfigMap.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + fullPath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + fullPath, e);
        }
    }

    private String configMapName(CardanoNode cr) {
        if (cr.getSpec() != null && cr.getSpec().getDevnetConfigMap() != null
                && !cr.getSpec().getDevnetConfigMap().isBlank()) {
            return cr.getSpec().getDevnetConfigMap();
        }
        return cr.getMetadata().getName() + "-devnet-config";
    }
}
