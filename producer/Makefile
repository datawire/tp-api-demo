# Delete implicit rules not used here (clutters debug output).
MAKEFLAGS += --no-builtin-rules
TELEPRESENCE_REGISTRY ?= docker.io/datawire

.DEFAULT_GOAL := target/deploy.ts

target/build.ts: $(wildcard src/**/*)
	mvn clean package
	mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
	touch target/build.ts

target/image.ts: target/build.ts
	DOCKER_BUILDKIT=1 docker build --tag tp-api-demo-producer .
	docker tag tp-api-demo-producer:latest $(TELEPRESENCE_REGISTRY)/tp-api-demo-producer
	touch target/image.ts

target/push-image.ts: target/image.ts
	docker push $(TELEPRESENCE_REGISTRY)/tp-api-demo-producer
	touch target/push-image.ts

target/deploy.ts: target/push-image.ts
	kubectl delete -f k8s || true
	kubectl apply -f k8s
	touch target/deploy.ts
