# Yaci Operator Helm Chart

Helm chart for deploying the [Yaci Operator](https://github.com/bloxbean/yaci-devkit) — a Kubernetes operator that manages Cardano nodes and the Yaci Store/indexer stack.

## What it does

The Yaci Operator watches `CardanoNode` custom resources and automatically provisions:

- **Cardano Node** — StatefulSet running the cardano-node container (mainnet, testnet, preprod, or devnet)
- **Devnet Config & Keys** — ConfigMap with genesis files and Secret with pool keys (devnet mode only)
- **Yaci Store** — Deployment running the yaci-store indexing service (with devnet)
- **Yaci Store UI** — Deployment running the yaci-viewer web UI (with devnet)

All managed resources have owner references set, so deleting the `CardanoNode` CR cleans up everything.

## Prerequisites

- Kubernetes 1.28+
- Helm 3.2+

## Installing the Chart

```bash
# Add the repo (if published)
helm repo add yaci https://bloxbean.github.io/yaci-helm-charts
helm repo update

# Install (namespace-scoped — recommended for dev/test)
helm install yaci-operator yaci/yaci-operator \
  --namespace cardano \
  --create-namespace
```

### Cluster-wide vs Namespace-scoped

By default the operator runs **namespace-scoped** (watches only its own namespace). To watch all namespaces:

```bash
helm install yaci-operator yaci/yaci-operator \
  --namespace cardano \
  --create-namespace \
  --set rbac.clusterWide=true
```

To watch specific namespaces while still namespace-scoped:

```bash
helm install yaci-operator yaci/yaci-operator \
  --namespace cardano \
  --create-namespace \
  --set rbac.targetNamespaces={cardano,staging}
```

## Configuration

See [values.yaml](./values.yaml) for all configurable parameters. Key options:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `image.registry` | Container registry | `ghcr.io` |
| `image.repository` | Image repo path | `bloxbean/yaci-operator` |
| `image.tag` | Image tag | (chart AppVersion) |
| `rbac.clusterWide` | Watch all namespaces | `false` |
| `rbac.targetNamespaces` | Namespaces to watch (when clusterWide=false) | `[]` (release namespace) |
| `crds.install` | Install the CRD | `true` |
| `service.enabled` | Expose operator metrics | `false` |
| `resources` | CPU/Memory requests/limits | `100m/256Mi` req, `500m/512Mi` limit |

## Creating a CardanoNode

After the operator is running, create a `CardanoNode` CR:

### Mainnet Node

```yaml
apiVersion: com.bloxbean/v1
kind: CardanoNode
metadata:
  name: cardano-node-sample
spec:
  image: blinklabs/cardano-node:10.5.1
  replicas: 1
  network: mainnet
```

### Devnet with Yaci Store + UI

```yaml
apiVersion: com.bloxbean/v1
kind: CardanoNode
metadata:
  name: cardano-node-devnet
spec:
  network: devnet
  image: blinklabs/cardano-node:10.5.1
  replicas: 1
  yaciStoreEnabled: true
```

### Full Spec Example

```yaml
apiVersion: com.bloxbean/v1
kind: CardanoNode
metadata:
  name: full-devnet
spec:
  network: devnet
  image: blinklabs/cardano-node:10.5.1
  replicas: 1
  storage: 20Gi

  # Override default devnet config/keys names
  devnetConfigMap: my-custom-config
  devnetKeysSecret: my-custom-keys

  yaciStoreEnabled: true
  yaciStoreImage: bloxbean/yaci-store:2.0.0
  yaciStoreUiImage: bloxbean/yaci-viewer:0.10.6
  yaciStoreStorage: 20Gi
```

## Checking Status

```bash
# List all CardanoNodes
kubectl get cardanonodes

# Detailed status (includes node + store + store-ui phases)
kubectl describe cardanonode <name>

# Get status in YAML
kubectl get cardanonode <name> -o yaml
```

Status fields include:
- `phase` / `message` — Cardano node StatefulSet status
- `storePhase` / `storeMessage` — Yaci Store deployment status
- `storeUiPhase` / `storeUiMessage` — Yaci Store UI deployment status

## Uninstalling

```bash
# Delete all CardanoNode CRs first (cleans up managed resources)
kubectl delete cardanonodes --all -n cardano

# Then uninstall the operator
helm uninstall yaci-operator -n cardano
```

> **Note:** CRDs are not deleted by `helm uninstall` by default. To remove them:
> ```bash
> kubectl delete crd cardanonodes.com.bloxbean
> ```

## Building from Source

```bash
# Build the operator JAR
cd applications/operator
./gradlew build

# Build and push the Docker image
docker build -t ghcr.io/bloxbean/yaci-operator:0.1.0 .
docker push ghcr.io/bloxbean/yaci-operator:0.1.0

# Package the chart
helm package helm/yaci-operator/
```
