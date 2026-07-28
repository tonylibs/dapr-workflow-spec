#!/bin/bash
if [ "$CLAUDE_CODE_REMOTE" != "true" ]; then exit 0; fi

service docker start 2>/dev/null || true
if ! kind get clusters | grep -q test-cluster; then
  kind create cluster --name test-cluster
fi
kubectl cluster-info --context kind-test-cluster