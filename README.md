# Demo of a Kafka implementation using the Telepresence RESTful API service

This repository contains two applications, the producer and the consumer, intended to be built and deployed to a Kubernetes cluster where Kafka has been installed already.

## Kafka installation
A simple way of installing Kafka (and Zookeeper) is to use https://github.com/bitnami/charts/tree/master/bitnami/kafka

## Telepresence configuration
Telepresence must be configured to use its RESTful API service. Add the following to the `config.yml` file:
```yaml
telepresenceAPI:
  port: 9980
```
Note that it's essential that this is done before the first intercept is made because it affects the environment of both the intercepted container and the traffic-agent sidecar. It is possible to make this setting persistent in the cluster by reinstalling the traffic-manager. The setting is then propagated to the Helm chart so that the traffic manager remembers it and configures all subsequent agent installs.

## Build and deploy the images
Both apps have a simple Makefile that will build the Java application, create a Docker container, publish it to Docker, and apply a manifest that contains a service and a deployment. Assuming that connectivity to the cluster is OK and that the cluster has Kafka up and running, deploying both apps should be a easy as:

```console
$ cd producer
$ make
$ cd ../consumer
$ make
```
## Run the demo

### Start an intercept
Use telepresence to intercept the consumer. The interceptor should start the same docker container on the workstation, e.g.
```console
$ telepresence intercept --mount=false --docker-run tp-api-demo-consumer --http-match=foo=bar -- --network host tp-api-demo-consumer:latest
```
This will produce log output as Kafka configures the consumer on the workstation.

Start logging of the tp-api-demo-consumer pod in a separate terminal window (the exact pod name can be found using `kubectl get pods`):
```console
$ kubectl logs -f tp-api-demo-consumer-5bb5688c9c-psswt apitest
```
This will result in similar log output from the consumer running in the cluster.

### Curl the producer
Sending curl requests to the producers "/send" endpoint will make it produce Kafka messages with the headers copied from the http request. The whole point of this demo is to show how those headers affect which consumer (the one running on the worksation or the one running in the cluster) that will consume the messages.

First try a message without any headers:
```console
$ curl -X POST http://tp-api-demo-producer.default/send/a-key
```
The log output from the pod will show something similar to:
```
16:54:49.271 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  i.d.t.consumer.TelepresenceAPIFilter - consume-here returned true
16:54:49.272 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  i.d.tpapidemo.consumer.Listener - Received Message in group demoGroup: some payload
```
and the corresponding output from the intercept will show:
```
16:54:49.272 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  i.d.t.consumer.TelepresenceAPIFilter - consume-here returned false
```
This shows that the message was accepted by the container running in the pod but filtered out by the container running locally. Now try the same URL and provide the intercept header `foo: bar` which was passed in the `--http-match` flag when the intercept was established:
```console
$ curl -H 'foo: bar' -X POST http://tp-api-demo-producer.default/send/a-key
```
Now the container in the pod rejects the message while the one running locally receives it.

And that concludes the demo.
