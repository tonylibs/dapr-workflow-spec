# dws-mid-basic: Wasm guest for daprd's middleware.http.wasm Component.
#
# The guest module (this directory) is Go 1.23 and builds with TinyGo 0.34.0 only; see CLAUDE.md.
# wasmtest/ is a separate module that runs the built bin/basic.wasm in the http-wasm host library.

IMAGE         ?= ghcr.io/tonylibs/dws-mid-basic:dev
TINYGO_VERSION := 0.34.0
# Set TINYGO=/path/to/tinygo (0.34.0 exactly) to build without Docker.
TINYGO        ?=
# The one build command. Keep the Dockerfile's build stage identical:
#   tinygo build -o /basic.wasm -scheduler=none --no-debug -target=wasi .
TINYGO_FLAGS  := -scheduler=none --no-debug -target=wasi
WASM          := bin/basic.wasm
BUILD_IMAGE   := dws-mid-basic-build:local

.PHONY: lint vet fmt-check test wasm test-wasm docker clean

## lint: go vet + gofmt check, for the guest module and wasmtest
lint: vet fmt-check

## vet: run go vet on the guest module and on wasmtest
vet:
	go vet ./...
	cd wasmtest && go vet ./...

## fmt-check: fail if any file is not gofmt-formatted
fmt-check:
	@unformatted=$$(gofmt -l .); \
	if [ -n "$$unformatted" ]; then \
		echo "gofmt needed on:"; echo "$$unformatted"; exit 1; \
	fi

## test: run the guest module's unit tests (plain Go, no TinyGo or Wasm runtime)
test:
	go test ./...

## wasm: build bin/basic.wasm with TinyGo 0.34.0 (Docker by default; TINYGO=/path/to/tinygo builds locally)
wasm:
	@mkdir -p bin
ifeq ($(strip $(TINYGO)),)
	docker build --target build -t $(BUILD_IMAGE) .
	@cid=$$(docker create $(BUILD_IMAGE)) && \
	docker cp "$$cid:/basic.wasm" $(WASM) && \
	docker rm "$$cid" >/dev/null
else
	@v=$$($(TINYGO) version 2>&1); \
	case "$$v" in \
		"tinygo version $(TINYGO_VERSION) "*) ;; \
		*) echo "TINYGO=$(TINYGO) must be TinyGo $(TINYGO_VERSION) (http-wasm-guest-tinygo v0.4.0 breaks on newer); got: $$v" >&2; exit 1 ;; \
	esac
	$(TINYGO) build -o $(WASM) $(TINYGO_FLAGS) .
endif
	@echo "built $(WASM): $$(wc -c < $(WASM)) bytes"

## test-wasm: run wasmtest against bin/basic.wasm (plain go, no Docker; run `make wasm` first)
test-wasm:
	@test -f $(WASM) || { echo "$(WASM) not found: run 'make wasm' first" >&2; exit 1; }
	cd wasmtest && BASIC_WASM="$(CURDIR)/$(WASM)" go test -count=1 ./...

## docker: build the container image (needs Docker)
docker:
	docker build -t $(IMAGE) .

## clean: remove build artifacts
clean:
	rm -rf bin
