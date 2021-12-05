# Delete implicit rules not used here (clutters debug output).
MAKEFLAGS += --no-builtin-rules
TELEPRESENCE_REGISTRY ?= docker.io/datawire

.DEFAULT_GOAL := push-image

.PHONY: build
build: $(wildcard src/**/*)
	mvn clean package
	mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

.PHONY: image
image: build
	DOCKER_BUILDKIT=1 docker build --tag tp-api-demo-consumer .
	docker tag tp-api-demo-consumer:latest $(TELEPRESENCE_REGISTRY)/tp-api-demo-consumer

.PHONY: push-image
push-image: image
	docker push $(TELEPRESENCE_REGISTRY)/tp-api-demo-consumer
