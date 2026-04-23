package com.bloxbean.cardano.operator.app;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration
@Workflow(dependents = {
    @Dependent(type = NodeDeployment.class)
})
public class CardanoNodeReconciler implements Reconciler<CardanoNode> {

    @Override
    public UpdateControl<CardanoNode> reconcile(CardanoNode cardanoNode, Context<CardanoNode> context) {
        // The workflow will handle the dependent resources (NodeDeployment)
        // We can add status updates here if needed
        return UpdateControl.noUpdate();
    }
}
