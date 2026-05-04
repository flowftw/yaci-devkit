package com.bloxbean.cardano.operator.app;

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
        @Dependent(type = NodeStatefulSet.class)
})
public class CardanoNodeReconciler implements Reconciler<CardanoNode> {

    @Override
    public UpdateControl<CardanoNode> reconcile(CardanoNode cardanoNode, Context<CardanoNode> context) {
        CardanoNodeStatus current = cardanoNode.getStatus();
        CardanoNodeStatus desired = copy(current);

        StatefulSet statefulSet = context.getSecondaryResource(StatefulSet.class).orElse(null);

        desired.setObservedGeneration(cardanoNode.getMetadata().getGeneration());
        desired.setDeploymentName(cardanoNode.getMetadata().getName());

        int requestedReplicas = 1;
        if (cardanoNode.getSpec() != null && cardanoNode.getSpec().getReplicas() != null) {
            requestedReplicas = cardanoNode.getSpec().getReplicas();
        }

        desired.setReplicas(requestedReplicas);

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

        if (!statusEquals(current, desired)) {
            desired.setLastReconciledAt(OffsetDateTime.now().toString());
            cardanoNode.setStatus(desired);
            return UpdateControl.patchStatus(cardanoNode);
        }

        return UpdateControl.noUpdate();
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
        return copy;
    }

    private boolean statusEquals(CardanoNodeStatus a, CardanoNodeStatus b) {
        return java.util.Objects.equals(a, b);
    }
}
