#!/usr/bin/env bash
set -euo pipefail

source_file="/tmp/host-codex-auth.json"
destination_dir="${HOME}/.codex"

if [ ! -f "${source_file}" ]; then
  echo "Codex auth file is missing from the read-only host mount." >&2
  exit 1
fi

umask 077
mkdir -p "${destination_dir}"
install -m 600 "${source_file}" "${destination_dir}/auth.json"
