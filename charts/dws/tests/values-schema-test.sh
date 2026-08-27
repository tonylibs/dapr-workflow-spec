#!/usr/bin/env bash
set -euo pipefail

chart_dir="${1:-charts/dws}"
rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT

helm template dws "$chart_dir" \
  --set console.enabled=true \
  --set adminGateway.enabled=true \
  --set adminGateway.corsOrigins[0]=https://console.example.test \
  --set defaults.resources.requests.cpu=125m \
  --set defaults.resources.requests.memory=192Mi \
  --set defaults.resources.limits.memory=384Mi \
  --set defaults.nodeSelector.workload=dws \
  --set defaults.tolerations[0].key=reserved \
  --set defaults.tolerations[0].operator=Exists \
  --set defaults.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].key=workload \
  --set defaults.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].operator=In \
  --set defaults.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].values[0]=dws \
  > "$rendered"

deployments="$(grep -c '^kind: Deployment$' "$rendered")"
test "$deployments" -eq 4

for value in 'cpu: 125m' 'memory: 192Mi' 'memory: 384Mi' 'workload: dws' 'key: reserved'; do
  test "$(grep -c "^            $value$" "$rendered")" -eq 4
done
