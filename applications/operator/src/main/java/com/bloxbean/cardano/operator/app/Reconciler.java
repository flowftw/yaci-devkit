package com.bloxbean.cardano.operator.app;

import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {
    @Dependent(type = NodeDeployment.class)
})
public class Reconciler {
    
}
