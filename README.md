# Demo of a Kafka and Telepresence RESTful API

## Objective

Show how a Kafka filter can use the Telepresence RESTful API to determine if a message should be consumed or not by
matching the message headers against headers controlling ongoing intercepts. A header match means that the message
should not be consumed in the cluster, but should be consumed in on the local workstation that performs an intercept.

## Prerequisites

- Telepresence version 2.4.8 or later, with
  the [RESTful API server](https://www.telepresence.io/docs/latest/reference/restapi) enabled.
- A Kubernetes cluster where Kafka is available
- The `tp-api-demo-producer` and `tp-api-demo-consumer` docker images provided by this repository
- Java version 11 or later
- Docker
- Maven

## Kafka installation

Unless you already have Kafka running in a cluster, a simple way of installing it (and Zookeeper) is to
use [bitnami/kafka](https://github.com/bitnami/charts/tree/master/bitnami/kafka).

## Telepresence configuration

Telepresence must be configured to use its RESTful API service. Add the following to the `config.yml` file:

```yaml
telepresenceAPI:
  port: 9980 # or whatever port number you prefer
```

Note that it's essential that this is done before the first intercept is made because it affects the environment of both
the intercepted container and the traffic-agent sidecar. It is possible to make this setting persistent in the cluster
by reinstalling the traffic-manager. The setting is then propagated to the Helm chart so that the traffic manager
remembers it and configures all subsequent agent installs.

## Build and deploy the images

Both producer and the consumer app have a simple Makefile that will build the Java application, create a Docker
container, publish it to Docker, and apply a manifest that contains a service and a deployment. Before use, please
ensure that you have cluster connectivity and that the `kafka.bootstrapAddress` in the app's _
src/resources/application.properties_ corresponds to address used by the cluster's the Kafka installation. Deploying
both apps should then be a easy as:

```console
$ cd producer
$ make
$ cd ../consumer
$ make
```

## Run the demo

### Start an intercept

Use telepresence to intercept the consumer. Use `--docker-run` and the same docker container as is deployed to the
cluster, e.g.

```console
$ telepresence intercept --docker-run tp-api-demo-consumer --http-match=foo=bar -- --network host tp-api-demo-consumer:latest
```

This will produce log output from Kafka as it configures the consumer on the workstation.

Start logging of the `tp-api-demo-consumer` pod in a separate terminal window (the exact pod name can be found
using `kubectl get pods`):

```console
$ kubectl logs -f tp-api-demo-consumer-5bb5688c9c-psswt apitest
```

This will result in similar log output from the consumer running in the cluster.

### Curl the producer

Sending curl requests to the producers "/send" endpoint will make it produce Kafka messages with the headers copied from
the http request. The whole point of this demo is to show how those headers affect which consumer (the one running on
the workstation or the one running in the cluster) that will consume the messages.

First try a message without any headers:

```console
$ curl -X POST http://tp-api-demo-producer.default/send/the-value
```

The log output from the pod will show something similar to:

```
16:54:49.271 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  i.d.t.consumer.TelepresenceAPIFilter - consume-here returned true
16:54:49.272 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  i.d.tpapidemo.consumer.Listener - Received Message in group demoGroup: the-value
```

and the corresponding output from the intercept will show:

```
16:54:49.272 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  i.d.t.consumer.TelepresenceAPIFilter - consume-here returned false
```

This shows that the message was accepted by the container running in the pod but filtered out by the container running
locally. Now try the same URL and provide the intercept header `foo: bar` which was passed in the `--http-match` flag
when the intercept was established:

```console
$ curl -H 'foo: bar' -X POST http://tp-api-demo-producer.default/send/the-value
```

Observe that it now is the pod's container that rejects the message while the one running locally accepts it. Providing
the header made all the difference.

## How does this demo work?

The producer listens to incoming http requests. When a request arrives to its "/send" endpoint, it creates a Kafka
message, copies all headers from the request, and sends the message on a named topic.

The consumer listens to the topic but uses a filter which calls the Telepresence API
on `http://localhost:<port>/consume-here` using the headers from the message. That endpoint then determines whether the
message should be consumed. If the headers match, only the intercept should consume it. If not, then the pod consumes
it. 