#!/usr/bin/env bash
set -euo pipefail

KC_CONTAINER=${KC_CONTAINER:-keycloak}

# Wait for Keycloak
until docker exec "$KC_CONTAINER" /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password admin >/dev/null 2>&1; do
  echo "Waiting for Keycloak..."
  sleep 2
done

docker exec "$KC_CONTAINER" /opt/keycloak/bin/kcadm.sh create realms -s realm=demo-client -s enabled=true || true
for role in admin user viewer editor; do
  docker exec "$KC_CONTAINER" /opt/keycloak/bin/kcadm.sh create roles -r demo-client -s name="$role" || true
done

echo "Realm baseline created. For full flow and mappers use realm import JSON."
