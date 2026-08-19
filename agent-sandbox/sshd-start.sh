#!/bin/sh
set -eu

# Host keys must be created per container; they are intentionally absent from the image.
mkdir -p /run/sshd
ssh-keygen -A

# Public keys must be supplied at runtime in /root/.ssh/authorized_keys.
exec /usr/sbin/sshd -D -e
