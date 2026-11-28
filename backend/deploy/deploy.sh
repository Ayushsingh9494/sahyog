#!/usr/bin/env bash
# Deploys the Sahyog server to Google Cloud (Mumbai): Cloud Run + Cloud SQL (PostgreSQL 17 +
# PostGIS) + a private Cloud Storage bucket for ID photos + Secret Manager for the DB password.
#
# Cheapest setup, for short test runs: the server scales to zero when idle (first request after a
# quiet spell takes ~10-15 s), the database is the smallest shared-core size with no backups.
# DELETE EVERYTHING WITH deploy/teardown.sh WHEN DONE: Cloud SQL is billed even when idle.
#
# Usage (from backend/):  ADMIN_PHONE_NUMBERS="+91XXXXXXXXXX" deploy/deploy.sh
# Safe to re-run: existing resources are kept, the server is rebuilt and redeployed.
set -euo pipefail

GCLOUD="${GCLOUD:-gcloud}"
PROJECT="${PROJECT:-sahyog-f6bad}"
REGION="${REGION:-asia-south1}"
INSTANCE="${INSTANCE:-sahyog-db}"
DB_NAME="sahyog"
DB_USER="sahyog"
BUCKET="${BUCKET:-${PROJECT}-id-documents}"
SERVICE="${SERVICE:-sahyog-api}"
SECRET="sahyog-db-password"
SA_NAME="sahyog-api"
SA="${SA_NAME}@${PROJECT}.iam.gserviceaccount.com"
: "${ADMIN_PHONE_NUMBERS:?Set ADMIN_PHONE_NUMBERS to the cooperative staff numbers (E.164, comma-separated)}"

g() { "$GCLOUD" --project "$PROJECT" --quiet "$@"; }
exists() { "$@" >/dev/null 2>&1; }

echo "== APIs"
g services enable run.googleapis.com sqladmin.googleapis.com cloudbuild.googleapis.com \
  artifactregistry.googleapis.com secretmanager.googleapis.com storage.googleapis.com

echo "== Service account for the server (least privilege)"
exists g iam service-accounts describe "$SA" ||
  g iam service-accounts create "$SA_NAME" --display-name "Sahyog API (Cloud Run)"
g projects add-iam-policy-binding "$PROJECT" --member "serviceAccount:$SA" --role roles/cloudsql.client --condition=None >/dev/null

echo "== Database (Cloud SQL, PostgreSQL 17, smallest size, no backups: test run only)"
if ! exists g sql instances describe "$INSTANCE"; then
  g sql instances create "$INSTANCE" --database-version=POSTGRES_17 --edition=ENTERPRISE \
    --tier=db-f1-micro --region="$REGION" --storage-type=HDD --storage-size=10 \
    --availability-type=zonal --no-backup --no-storage-auto-increase
fi
exists g sql databases describe "$DB_NAME" --instance "$INSTANCE" || g sql databases create "$DB_NAME" --instance "$INSTANCE"

echo "== Database password (random, kept only in Secret Manager)"
if ! exists g secrets describe "$SECRET"; then
  PASSWORD="$(openssl rand -base64 33 | tr -d '/+=\n' | cut -c1-32)"
  printf '%s' "$PASSWORD" | g secrets create "$SECRET" --replication-policy=user-managed --locations="$REGION" --data-file=-
  if exists g sql users describe "$DB_USER" --instance "$INSTANCE"; then
    g sql users set-password "$DB_USER" --instance "$INSTANCE" --password "$PASSWORD"
  else
    g sql users create "$DB_USER" --instance "$INSTANCE" --password "$PASSWORD"
  fi
  unset PASSWORD
fi
g secrets add-iam-policy-binding "$SECRET" --member "serviceAccount:$SA" --role roles/secretmanager.secretAccessor >/dev/null

echo "== Private bucket for ID photos"
if ! exists g storage buckets describe "gs://$BUCKET"; then
  g storage buckets create "gs://$BUCKET" --location="$REGION" --uniform-bucket-level-access --public-access-prevention
fi
g storage buckets add-iam-policy-binding "gs://$BUCKET" --member "serviceAccount:$SA" --role roles/storage.objectAdmin >/dev/null

echo "== Build and deploy the server"
(cd "$(dirname "$0")/.." && ./gradlew --console=plain -q bootJar)
# Settings go through a file: on Windows gcloud runs via cmd.exe, which mangles special characters
# (like ^) on the command line. The file holds no secrets and is deleted afterwards anyway.
ENV_FILE="$(mktemp)"
trap 'rm -f "$ENV_FILE"' EXIT
printf 'DB_INSTANCE: "%s"\nDOCUMENTS_BUCKET: "%s"\nFIREBASE_PROJECT_ID: "%s"\nADMIN_PHONE_NUMBERS: "%s"\n' \
  "$PROJECT:$REGION:$INSTANCE" "$BUCKET" "$PROJECT" "$ADMIN_PHONE_NUMBERS" > "$ENV_FILE"
g run deploy "$SERVICE" --source "$(dirname "$0")/.." --region "$REGION" \
  --service-account "$SA" --allow-unauthenticated \
  --add-cloudsql-instances "$PROJECT:$REGION:$INSTANCE" \
  --env-vars-file "$(cygpath -w "$ENV_FILE" 2>/dev/null || echo "$ENV_FILE")" \
  --set-secrets "DB_PASSWORD=$SECRET:latest" \
  --memory 1Gi --cpu 1 --cpu-boost --min-instances 0 --max-instances 2 --timeout 60

URL="$(g run services describe "$SERVICE" --region "$REGION" --format 'value(status.url)')"
echo "== Done: $URL"
echo "   Health: $URL/actuator/health"
echo "   App:    put SAHYOG_API_URL=$URL/ in local.properties and rebuild."
