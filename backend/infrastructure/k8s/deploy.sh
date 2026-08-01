#!/bin/bash
# ─── AdvisorConnect — Kubernetes Deploy Script ────────────────────────────────
# Usage: ./deploy.sh [namespace]
# Default namespace: advisorconnect-prod

set -euo pipefail

NAMESPACE="${1:-advisorconnect-prod}"
K8S_DIR="$(cd "$(dirname "$0")" && pwd)"

info()  { echo "[INFO]  $*"; }
error() { echo "[ERROR] $*" >&2; exit 1; }

command -v kubectl >/dev/null 2>&1 || error "kubectl not found. Install it first."

info "Deploying AdvisorConnect to namespace: $NAMESPACE"

# ── 1. Namespaces ─────────────────────────────────────────────────────────────
info "Applying namespaces..."
kubectl apply -f "$K8S_DIR/namespaces/"

# ── 2. RBAC ───────────────────────────────────────────────────────────────────
info "Applying RBAC..."
kubectl apply -f "$K8S_DIR/rbac.yaml"

# ── 3. Secrets (ensure secrets exist before deploying pods) ───────────────────
info "Applying secrets (placeholders — replace with real values before production)..."
kubectl apply -f "$K8S_DIR/secrets/"

# ── 4. ConfigMaps ─────────────────────────────────────────────────────────────
info "Applying ConfigMaps..."
kubectl apply -f "$K8S_DIR/configmaps/"

# ── 5. Deployments ────────────────────────────────────────────────────────────
info "Applying Deployments..."
kubectl apply -f "$K8S_DIR/deployments/"

# ── 6. Services ───────────────────────────────────────────────────────────────
info "Applying Services..."
kubectl apply -f "$K8S_DIR/services/"

# ── 7. Ingress ────────────────────────────────────────────────────────────────
info "Applying Ingress..."
kubectl apply -f "$K8S_DIR/ingress/"

# ── 8. HPA ────────────────────────────────────────────────────────────────────
info "Applying HPAs..."
kubectl apply -f "$K8S_DIR/hpa/"

# ── 9. PodDisruptionBudgets ───────────────────────────────────────────────────
info "Applying PodDisruptionBudgets..."
kubectl apply -f "$K8S_DIR/pdb/"

# ── 10. Wait for rollout ──────────────────────────────────────────────────────
info "Waiting for all deployments to be ready..."
for deploy in api-gateway auth-service user-service advisor-service chat-service booking-service notification-service admin-service frontend; do
  info "  Waiting for $deploy..."
  kubectl rollout status deployment/"$deploy" -n "$NAMESPACE" --timeout=5m
done

info "All deployments are live!"
info ""
info "Access points:"
info "  Frontend:     https://advisorconnect.io"
info "  API Gateway:  https://api.advisorconnect.io"
info "  Admin Panel:  https://admin.advisorconnect.io"
