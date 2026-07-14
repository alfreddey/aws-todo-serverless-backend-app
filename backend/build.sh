#!/usr/bin/env bash
# Build the single deployable fat jar (target/todo-backend.jar) that every
# Lambda function in template.yaml points at.
set -euo pipefail
cd "$(dirname "$0")"
mvn -q clean package
echo "Built: $(pwd)/target/todo-backend.jar"
