package com.bloxbean.cardano.operator.app;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
@ControllerConfiguration(generationAwareEventProcessing = true)
@Workflow(dependents = {
        @Dependent(type = NodeDeployment.class)
})
public class CardanoNodeReconciler implements Reconciler<CardanoNode> {

    @Override
    public UpdateControl<CardanoNode> reconcile(CardanoNode cardanoNode, Context<CardanoNode> context) {
        CardanoNodeStatus current = cardanoNode.getStatus();
        CardanoNodeStatus desired = copy(current);

        Deployment deployment = context.getSecondaryResource(Deployment.class).orElse(null);

        desired.setObservedGeneration(cardanoNode.getMetadata().getGeneration());
        desired.setDeploymentName(cardanoNode.getMetadata().getName());

        int requestedReplicas = 1;
        if (cardanoNode.getSpec() != null && cardanoNode.getSpec().getReplicas() != null) {
            requestedReplicas = cardanoNode.getSpec().getReplicas();
        }

        desired.setReplicas(requestedReplicas);

        if (deployment == null || deployment.getStatus() == null) {
            desired.setReadyReplicas(0);
            desired.setPhase("Pending");
            desired.setMessage("Waiting for Deployment to be created");
        } else {
            Integer ready = deployment.getStatus().getReadyReplicas();
            int readyReplicas = ready != null ? ready : 0;
            desired.setReadyReplicas(readyReplicas);

            if (readyReplicas >= requestedReplicas && requestedReplicas > 0) {
                desired.setPhase("Ready");
                desired.setMessage("Cardano node is ready");
            } else {
                desired.setPhase("Progressing");
                desired.setMessage("Cardano node deployment is progressing");
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
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }

        return Objects.equals(a.getPhase(), b.getPhase())
                && Objects.equals(a.getMessage(), b.getMessage())
                && Objects.equals(a.getDeploymentName(), b.getDeploymentName())
                && Objects.equals(a.getReplicas(), b.getReplicas())
                && Objects.equals(a.getReadyReplicas(), b.getReadyReplicas())
                && Objects.equals(a.getObservedGeneration(), b.getObservedGeneration());
    }
}
