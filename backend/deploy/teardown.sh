#!/usr/bin/env bash
# Deletes everything deploy/deploy.sh created, so nothing keeps billing. The database and the ID
# photos are deleted permanently: export first if you want to keep them (see backend/README.md).
#
# Usage (from backend/):  deploy/teardown.sh
set -euo pipefail

GCLOUD="${GCLOUD:-gcloud}"
PROJECT="${PROJECT:-sahyog-f6bad}"
REGION="${REGION:-asia-south1}"
INSTANCE="${INSTANCE:-sahyog-db}"
BUCKET="${BUCKET:-${PROJECT}-id-documents}"
SERVICE="${SERVICE:-sahyog-api}"
SECRET="sahyog-db-password"
SA="sahyog-api@${PROJECT}.iam.gserviceaccount.com"

g() { "$GCLOUD" --project "$PROJECT" --quiet "$@"; }
try() { "$@" || echo "   (already gone)"; }

echo "== Cloud Run service";        try g run services delete "$SERVICE" --region "$REGION"
echo "== Cloud SQL instance";       try g sql instances delete "$INSTANCE"
echo "== ID photo bucket";          try g storage rm --recursive "gs://$BUCKET"
echo "== Database password secret"; try g secrets delete "$SECRET"
echo "== Payment gateway key secret"; try g secrets delete "sahyog-razorpay-key-secret"
echo "== Server images";            try g artifacts repositories delete cloud-run-source-deploy --location "$REGION"
echo "== Service account";          try g iam service-accounts delete "$SA"

echo "== Left over (should be empty):"
g run services list --region "$REGION" --format 'value(name)'
g sql instances list --format 'value(name)'
g storage buckets list --format 'value(name)' | grep -F "$BUCKET" || true
