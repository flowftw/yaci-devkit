package com.bloxbean.cardano.operator.app;

import com.bloxbean.cardano.yacicli.genesis.config.GenesisConfig;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

@KubernetesDependent
public class DevnetConfigMap extends CRUDKubernetesDependentResource<ConfigMap, CardanoNode> {

    private static final String TEMPLATE_BASE = "genesis-templates";
    private static final Mustache.Compiler COMPILER = Mustache.compiler();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public DevnetConfigMap() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(CardanoNode primary, Context<CardanoNode> context) {
        String name = configMapName(primary);
        Map<String, Object> values = buildGenesisValues(primary);

        String byronGenesis = render("byron-genesis.json", values);
        String shelleyGenesis = render("shelley-genesis.json", values);

        // Inject dynamic start times into the rendered JSON
        byronGenesis = injectByronStartTime(byronGenesis, (Long) values.get("startTime"));
        shelleyGenesis = injectShelleySystemStart(shelleyGenesis, (String) values.get("systemStart"));

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
                        "byron-genesis.json", byronGenesis,
                        "shelley-genesis.json", shelleyGenesis,
                        "alonzo-genesis.json", render("alonzo-genesis.json", values),
                        "conway-genesis.json", render("conway-genesis.json", values)
                ))
                .build();
    }

    /**
     * Build the Mustache values map using the shared GenesisConfig from genesis-config module,
     * with operator-specific runtime defaults.
     *
     * Start times are derived from the CardanoNode CR's {@code creationTimestamp} so they are
     * stable across reconciliations and do not require reading the live cluster.
     */
    private Map<String, Object> buildGenesisValues(CardanoNode primary) {
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

        // Derive stable start times from the CR's creationTimestamp.
        // This guarantees every reconciliation produces the exact same genesis,
        // eliminating the need to read the live ConfigMap inside desired().
        Long startTime;
        String systemStart;

        String creationTimestamp = primary.getMetadata().getCreationTimestamp();
        if (creationTimestamp != null && !creationTimestamp.isBlank()) {
            Instant createdAt = Instant.parse(creationTimestamp);
            startTime = createdAt.getEpochSecond();
            systemStart = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .format(createdAt.atZone(ZoneOffset.UTC));
        } else {
            // Fallback if creationTimestamp is somehow missing (should never happen for persisted CRs)
            Instant now = Instant.now();
            startTime = now.getEpochSecond();
            systemStart = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .format(now.atZone(ZoneOffset.UTC));
        }

        values.put("startTime", startTime);
        values.put("systemStart", systemStart);

        return values;
    }

    private String injectByronStartTime(String json, long startTime) {
        try {
            ObjectNode node = (ObjectNode) OBJECT_MAPPER.readTree(json);
            node.put("startTime", startTime);
            return OBJECT_MAPPER.writer(new DefaultPrettyPrinter()).writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject byron startTime", e);
        }
    }

    private String injectShelleySystemStart(String json, String systemStart) {
        try {
            ObjectNode node = (ObjectNode) OBJECT_MAPPER.readTree(json);
            node.put("systemStart", systemStart);
            return OBJECT_MAPPER.writer(new DefaultPrettyPrinter()).writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject shelley systemStart", e);
        }
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
        try (var is = DevnetConfigMap.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
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
