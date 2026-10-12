#!/usr/bin/env bash
# waitstable.sh <sha-prefix>: like waitci.sh, for a [release] commit (waits for the stable-release job).
JOB=stable-release exec bash "$(dirname "$0")/waitci.sh" "$@"
