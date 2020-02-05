### Obtain a Kubernetes namespace

```java
@KubernetesEcosystem
public IKubernetesEcosystem ecosystem;
    
@KubernetesNamespace
public IKubernetesNamespace namespace;
```

This code requests that the Galasa Ecosystem is provisioned in a Kubernetes Namespace.  The default tag for the Ecosystem and the Namespace is PRIMARY.

### Retrieve the RAS Endpoint

```java
@KubernetesEcosystem
public IKubernetesEcosystem ecosystem;

URI ras = ecosystem.getEndpoint(EcosystemEndpoint.RAS);

```

This snippet demonstrates how to retrieve the Result Archive Store endpoint.  Note that the URI is 
prefixed with the store type, eg couchdb:http://couchdb.server:5984.  This is the same for the CPS, DSS and CREDS.

### Set and retrieve a CPS property

```java
ecosystem.setCpsProperty("bob", "hello");

String value = ecosystem.getCpsProperty("bob")
```

Sets the CPS property `bob` with the value `hello` and retrieves it again.