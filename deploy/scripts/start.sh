#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_env
compose up -d
"${SCRIPT_DIR}/health-check.sh" --wait

