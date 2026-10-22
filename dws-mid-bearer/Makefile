IMAGE ?= registry.io/dws/dws-mid-bearer:dev

.PHONY: build test lint vet fmt-check docker clean

## build: compile the package on the host toolchain (a sanity check only;
## the real artifact is the .wasm built by `make docker` with TinyGo 0.34.0)
build:
	go build ./...

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

## docker: build the delivery image (TinyGo 0.34.0 inside the Dockerfile)
docker:
	docker build -t $(IMAGE) .

## clean: remove build artifacts
clean:
	rm -rf bin
