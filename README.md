# Deliverables Analyzer Generator Service

The **Deliverables Analyzer Generator Service** is a specialized microservice within the SBOMer architecture responsible for executing SBOM generation requests based on deliverables analyzed by the [Project Newcastle (PNC) Deliverables Analyzer](https://github.com/project-ncl).

It acts as an **Event-Driven Processor** that listens for generation events, retrieves complex analysis data from PNC, maps it into CycloneDX specification, and uploads the resulting SBOMs.

## Architecture

This service follows **Hexagonal Architecture (Ports and Adapters)** to decouple the business logic of SBOM construction from external infrastructure constraints like Kafka or PNC APIs.

### 1. Core Domain (Business Logic)
* **`DelaGenerationService`:** The "Brain". It manages the orchestration of the generation request, groups analyzed artifacts by their parent deliverables (ZIPs/TGZs), orchestrates the SBOM construction, and handles the overarching failure state machine.
* **`CycloneDxMapper`:** Translates raw PNC `AnalyzedArtifact` objects into CycloneDX `Component` objects. It handles coordinate extraction (Maven/NPM), hashes, SPDX licenses, PNC traceability, and ecosystem-specific tagging.
* **`NpmDependencyWorkaroundService`:** Identifies components built by non-NPM PNC builds and dynamically injects their hidden build-time NPM dependencies into the CycloneDX tree.

### 2. Driving Adapters (Input)
* **`KafkaRequestConsumer`:** Listens to the `generation.created` topic. If the request's target type matches `PNC_DELA`, it initiates the generation process.

### 3. Driven Adapters (Output)
* **`PncServiceAdapter`:** Communicates with the PNC APIs to retrieve Operation metadata, Analyzed Artifacts, and NPM Dependencies. It wraps the official PNC Java clients and includes a Quarkus REST Client fallback (`PncRestApiClient`).
* **`HttpStorageServiceAdapter`:** Packages the generated JSON SBOMs into `multipart/form-data` payloads and uploads them atomically to the [Manifest Storage Service](https://github.com/sbomer-project/manifest-storage-service).
* **`KafkaStatusUpdateService` & `KafkaFailureNotifier`:** Sends `generation.update` events (GENERATING, FINISHED, FAILED) back to the control plane, and routes catastrophic runtime errors to the `sbomer.errors` dead-letter queue.

---

## Features

### 1. Multi-Deliverable Support
A single PNC Operation can analyze multiple archives. The service automatically groups artifacts by `distributionUrl` and generates a distinct SBOM for every deliverable archive found in the operation, uploading them in a batch.

### 2. Red Hat Ecosystem Tagging
The service automatically detects Red Hat artifacts (via `.redhat-xxxxx` version suffixes or `%40redhat` PURLs). When detected, it actively stamps the CycloneDX components with Red Hat Publisher, Supplier, and MRRC distribution metadata.

### 3. Deep PNC Traceability
To ensure complete provenance, the generator injects CycloneDX `ExternalReferences` into every component, linking directly back to the PNC Artifact, the PNC Build, the Environment Image used during the build, and the upstream VCS repository.

### 4. Safe NPM Parsing & Workarounds
The service implements graceful fallbacks for malformed NPM coordinates originating from upstream libraries. Additionally, it actively fetches PNC Builds to discover and inject hidden NPM dependencies that the standard Deliverables Analyzer might miss during Java-based builds.

### 5. Deterministic Serial Numbers
To ensure reproducible builds and predictable updates, the service generates UUID v3 (Name-based) serial numbers based on the raw JSON content of the BOM before finalizing the document.

---

## Configuration

| Property                                     | Description                                                                 | Default                  |
|:---------------------------------------------|:----------------------------------------------------------------------------|:-------------------------|
| `sbomer.generator.tool.name`                 | The name of the tool injected into the Root Component metadata.             | `SBOMer NextGen`         |
| `sbomer.generator.tool.version`              | The version of the tool injected into the Root Component metadata.          | `1.0.0`                  |
| `sbomer.generator.supplier.name`             | The supplier name injected into the Root Component metadata.                | `Red Hat`                |
| `sbomer.generator.supplier.urls`             | Comma-separated list of supplier URLs for the Root Component metadata.      | `https://www.redhat.com` |
| `pnc.api.url`                                | The base URL for the PNC official Java Clients to fetch analysis data.      | *(Environment specific)* |
---

## Development Environment Setup

We can run this component in a **Minikube Environment** by injecting it as part of the sbomer-platform helm chart and installing it into our cluster.

We provide helper scripts in the `hack/` directory to automate the networking and configuration between these two environments.

### 1. Prerequisites
* **Podman** (or Docker)
* **Minikube**
* **Helm**
* **Maven** & **Java 17+**
* **Kubectl**

### 2. Prepare the Cluster
First, we need to ensure we have the `sbomer` Minikube profile running with Tekton and Kafka installed.

To do this we have a dedicated repository and script:

```bash
./hack/setup-local-dev.sh
```

### 3. Run the Component with Helm in Minikube
Use the `./hack/run-helm-with-local-build.sh` script to start the system. This script performs several critical steps:

- Clones `sbomer-platform` into the component repository.
- Builds the component image (`dela-generator`) and loads it directly into the Minikube registry.
- Injects the locally built component values into the `sbomer-platform` Helm chart.
- Installs the `sbomer-platform` Helm chart with our locally built component.

### 4. Verify the Installation
Once the script completes, you can verify the status of the generator pod:

```bash
kubectl get pods -n sbomer
```

You can then use the provided test script in the hack/ directory to trigger a test generation after port-forwarding the API Gateway (mentioned at the end of the `./hack/run-helm-with-local-build.sh` script):

```bash
./hack/test-dela-gen.sh
```

*(Note: To avoid PNC connection setup, the minikube deployment uses the PNC Mock Adapter to generate mock SBOMs, the prod deployment without the mock profile defined, will use the real PNC Adapter).*