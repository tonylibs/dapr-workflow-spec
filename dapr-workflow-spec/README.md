# dapr-workflow-spec

The DWS controller: it accepts Serverless Workflow DSL 1.0 definitions, compiles them with the
Serverless Workflow SDK, and deploys one stack per definition — an immutable definition ConfigMap,
a Dapr configuration Component, one Knative Service per I/O task (from prebuilt images), and a
dedicated orchestrator Deployment.

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/workflows` | Body is DSL 1.0 YAML or JSON. `201` on first deploy, `200` when that exact version is already deployed, `400` with an error list when invalid. `?dryRun=true` returns the computed plan without applying. |
| `GET` | `/workflows` | List: name, version, status. |
| `GET` | `/workflows/{name}` | Detail, including the current version and every version present. |
| `GET` | `/workflows/{name}/plan` | Recomputes the `DeploymentPlan` from the deployed definition. |
| `DELETE` | `/workflows/{name}` | Tears down the whole stack by label selector. |

Versions are content-addressed: `<workflow>@v<sha256-8>` of the normalized definition, so
re-posting identical content is a no-op. There is no database — every read comes from live
cluster state selected by the `dws.io/*` labels.

Configure the prebuilt images and target namespace in `src/main/resources/application.yaml`
(`dws.images.call-http`, `.call-openapi`, `.run`, `.orchestrator`, `dws.namespace`).

See [k8s/README.md](k8s/README.md) for deployment, RBAC and a worked `POST order.yaml` flow.

## Quarkus

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/dapr-workflow-spec-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- Kubernetes Client ([guide](https://quarkus.io/guides/kubernetes-client)): Interact with Kubernetes and develop Kubernetes Operators

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
