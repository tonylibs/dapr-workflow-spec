BINARY := dws-run

REGISTRY ?= registry.io/dws
TAG      ?= 1.0

.PHONY: build test lint vet fmt-check docker-shell docker-script-js docker-script-python docker clean

## build: compile the server binary
build:
	CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o bin/$(BINARY) .

## test: run all unit tests with the race detector
test:
	go test -race ./...

## vet: run go vet
vet:
	go vet ./...

## fmt-check: fail if any file is not gofmt-formatted
fmt-check:
	@unformatted=$$(gofmt -l .); \
	if [ -n "$$unformatted" ]; then \
		echo "gofmt needed on:"; echo "$$unformatted"; exit 1; \
	fi

## lint: vet + formatting check (uses golangci-lint when available)
lint: vet fmt-check
	@if command -v golangci-lint >/dev/null 2>&1; then \
		golangci-lint run ./...; \
	else \
		echo "golangci-lint not installed; ran go vet + gofmt only"; \
	fi

## docker-shell: build the shell runtime image
docker-shell:
	docker build -f Dockerfile.shell -t $(REGISTRY)/dws-run-shell:$(TAG) .

## docker-script-js: build the JavaScript runtime image
docker-script-js:
	docker build -f Dockerfile.script-js -t $(REGISTRY)/dws-run-script-js:$(TAG) .

## docker-script-python: build the Python runtime image
docker-script-python:
	docker build -f Dockerfile.script-python -t $(REGISTRY)/dws-run-script-python:$(TAG) .

## docker: build all three images
docker: docker-shell docker-script-js docker-script-python

## clean: remove build artifacts
clean:
	rm -rf bin
