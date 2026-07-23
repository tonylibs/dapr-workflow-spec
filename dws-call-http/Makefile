IMAGE ?= registry.io/dws/dws-call-http:1.0
BINARY := dws-call-http

.PHONY: build test lint vet fmt-check docker clean

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

## docker: build the container image
docker:
	docker build -t $(IMAGE) .

## clean: remove build artifacts
clean:
	rm -rf bin
