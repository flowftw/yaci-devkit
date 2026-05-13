package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
@ControllerConfiguration(generationAwareEventProcessing = true)
@Workflow(dependents = {
        @Dependent(name = "devnetConfig", type = DevnetConfigMap.class, reconcilePrecondition = DevnetModeCondition.class),
        @Dependent(name = "devnetKeys", type = DevnetKeysSecret.class, reconcilePrecondition = DevnetModeCondition.class),
        @Dependent(name = "nodeSvc", type = NodeService.class),
        @Dependent(name = "nodeStatefulSet", type = NodeStatefulSet.class),
        @Dependent(name = "yaciStore", type = YaciStoreDeployment.class, reconcilePrecondition = YaciStoreCondition.class),
        @Dependent(name = "yaciStoreSvc", type = YaciStoreService.class, reconcilePrecondition = YaciStoreCondition.class),
        @Dependent(name = "yaciStoreUi", type = YaciStoreUiDeployment.class, reconcilePrecondition = YaciStoreCondition.class),
        @Dependent(name = "yaciStoreUiSvc", type = YaciStoreUiService.class, reconcilePrecondition = YaciStoreCondition.class)
})
public class CardanoNodeReconciler implements Reconciler<CardanoNode> {

    @Override
    public UpdateControl<CardanoNode> reconcile(CardanoNode cardanoNode, Context<CardanoNode> context) {
        CardanoNodeStatus current = cardanoNode.getStatus();
        CardanoNodeStatus desired = copy(current);

        // Use getSecondaryResources (plural) to avoid "More than 1 secondary resource"
        // errors when multiple dependents of the same type exist in the workflow
        String storeDeployName = cardanoNode.getMetadata().getName() + "-store";
        String storeUiDeployName = cardanoNode.getMetadata().getName() + "-store-ui";

        StatefulSet statefulSet = context.getSecondaryResources(StatefulSet.class).stream()
                .findFirst().orElse(null);

        Deployment storeDeployment = context.getSecondaryResources(Deployment.class).stream()
                .filter(d -> storeDeployName.equals(d.getMetadata().getName()))
                .findFirst().orElse(null);

        Deployment storeUiDeployment = context.getSecondaryResources(Deployment.class).stream()
                .filter(d -> storeUiDeployName.equals(d.getMetadata().getName()))
                .findFirst().orElse(null);

        desired.setObservedGeneration(cardanoNode.getMetadata().getGeneration());
        desired.setDeploymentName(cardanoNode.getMetadata().getName());

        int requestedReplicas = 1;
        if (cardanoNode.getSpec() != null && cardanoNode.getSpec().getReplicas() != null) {
            requestedReplicas = cardanoNode.getSpec().getReplicas();
        }

        desired.setReplicas(requestedReplicas);

        // Node status
        if (statefulSet == null || statefulSet.getStatus() == null) {
            desired.setReadyReplicas(0);
            desired.setPhase("Pending");
            desired.setMessage("Waiting for StatefulSet to be created");
        } else {
            Integer ready = statefulSet.getStatus().getReadyReplicas();
            int readyReplicas = ready != null ? ready : 0;
            desired.setReadyReplicas(readyReplicas);

            if (readyReplicas >= requestedReplicas && requestedReplicas > 0) {
                desired.setPhase("Ready");
                desired.setMessage("Cardano node is ready");
            } else {
                desired.setPhase("Progressing");
                desired.setMessage("Cardano node is progressing");
            }
        }

        // Store status
        if (!isStoreEnabled(cardanoNode)) {
            desired.setStorePhase(null);
            desired.setStoreMessage(null);
            desired.setStoreReadyReplicas(null);
            desired.setStoreUiPhase(null);
            desired.setStoreUiMessage(null);
            desired.setStoreUiReadyReplicas(null);
        } else {
            computeDeploymentStatus(storeDeployment, 1,
                    desired::setStorePhase, desired::setStoreMessage, desired::setStoreReadyReplicas,
                    "Yaci Store");
            computeDeploymentStatus(storeUiDeployment, 1,
                    desired::setStoreUiPhase, desired::setStoreUiMessage, desired::setStoreUiReadyReplicas,
                    "Yaci Store UI");
        }

        if (!statusEquals(current, desired)) {
            desired.setLastReconciledAt(OffsetDateTime.now().toString());
            cardanoNode.setStatus(desired);
            return UpdateControl.patchStatus(cardanoNode);
        }

        return UpdateControl.noUpdate();
    }

    private boolean isStoreEnabled(CardanoNode cardanoNode) {
        if (cardanoNode.getSpec() == null) return false;
        if (cardanoNode.getSpec().getYaciStoreEnabled() == null || !cardanoNode.getSpec().getYaciStoreEnabled())
            return false;
        String network = cardanoNode.getSpec().getNetwork();
        return "devnet".equalsIgnoreCase(network) || "local-devnet".equalsIgnoreCase(network);
    }

    private void computeDeploymentStatus(Deployment deployment, int expectedReplicas,
                                         java.util.function.Consumer<String> phaseSetter,
                                         java.util.function.Consumer<String> messageSetter,
                                         java.util.function.Consumer<Integer> readySetter,
                                         String label) {
        if (deployment == null || deployment.getStatus() == null) {
            readySetter.accept(0);
            phaseSetter.accept("Pending");
            messageSetter.accept(label + " deployment is being created");
        } else {
            Integer ready = deployment.getStatus().getReadyReplicas();
            int readyReplicas = ready != null ? ready : 0;
            readySetter.accept(readyReplicas);
            if (readyReplicas >= expectedReplicas) {
                phaseSetter.accept("Ready");
                messageSetter.accept(label + " is ready");
            } else {
                phaseSetter.accept("Progressing");
                messageSetter.accept(label + " is starting");
            }
        }
    }

    private CardanoNodeStatus copy(CardanoNodeStatus status) {
        CardanoNodeStatus copy = new CardanoNodeStatus();
        if (status == null) {
            return copy;
        }

        copy.setPhase(status.getPhase());
        copy.setMessage(status.getMessage());
        copy.setDeploymentName(status.getDeploymentName());
        copy.setReplicas(status.getReplicas());
        copy.setReadyReplicas(status.getReadyReplicas());
        copy.setObservedGeneration(status.getObservedGeneration());
        copy.setLastReconciledAt(status.getLastReconciledAt());
        copy.setStorePhase(status.getStorePhase());
        copy.setStoreMessage(status.getStoreMessage());
        copy.setStoreReadyReplicas(status.getStoreReadyReplicas());
        copy.setStoreUiPhase(status.getStoreUiPhase());
        copy.setStoreUiMessage(status.getStoreUiMessage());
        copy.setStoreUiReadyReplicas(status.getStoreUiReadyReplicas());
        return copy;
    }

    private boolean statusEquals(CardanoNodeStatus a, CardanoNodeStatus b) {
        return java.util.Objects.equals(a, b);
    }
}
