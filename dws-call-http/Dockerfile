# syntax=docker/dockerfile:1

# --- build stage ---
FROM golang:1.26 AS build
WORKDIR /src

# Cache module downloads separately from source.
COPY go.mod ./
RUN go mod download

COPY . .

# Static, stripped binary for a distroless runtime.
ENV CGO_ENABLED=0 GOOS=linux
RUN go build -trimpath -ldflags="-s -w" -o /out/dws-call-http .

# --- runtime stage ---
FROM gcr.io/distroless/static:nonroot
WORKDIR /
COPY --from=build /out/dws-call-http /dws-call-http

USER nonroot:nonroot
EXPOSE 8080
ENTRYPOINT ["/dws-call-http"]
