FROM tinygo/tinygo:0.34.0 AS build
USER root
WORKDIR /src
COPY guest/ ./
RUN go mod tidy && tinygo build -o /auth.wasm -scheduler=none --no-debug -target=wasi .

FROM busybox:1.37.0
COPY --from=build /auth.wasm /auth.wasm
