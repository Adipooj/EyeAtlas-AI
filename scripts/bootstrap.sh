#!/usr/bin/env bash
set -euo pipefail

echo "Bootstrapping repository environment..."
if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
    echo "Python is required" >&2
    exit 1
fi

echo "Environment check completed successfully."
