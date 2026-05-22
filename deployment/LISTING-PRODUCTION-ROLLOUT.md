# Listing Query Production Rollout Playbook

## Scope

Playbook ini hanya untuk rollout `listing-query-service` di production melalui monolith gateway.

## Preconditions

- Staging verification `listing-query-service` sudah lulus
- `listing-query-service` production sudah deployed
- `GET /actuator/health` service production stabil `UP`
- Monolith production dapat melayani `/api/listings*` secara local
- `LISTING_QUERY_SERVICE_FAIL_OPEN=true`

## Production env

Monolith:

- `LISTING_QUERY_SERVICE_BASE_URL=https://listing-query-production.example`
- `LISTING_QUERY_SERVICE_FAIL_OPEN=true`
- `LISTING_QUERY_SERVICE_ROLLOUT_MODE=SHADOW`
- `LISTING_QUERY_SERVICE_ROLLOUT_PERCENT=0`
- `LISTING_QUERY_SERVICE_CONNECT_TIMEOUT_MS=500`
- `LISTING_QUERY_SERVICE_READ_TIMEOUT_MS=1500`

Service:

- `PORT=8082`
- `SPRING_PROFILES_ACTIVE=production`
- `LISTING_QUERY_DB_URL=...`
- `LISTING_QUERY_DB_USERNAME=readonly_user`
- `LISTING_QUERY_DB_PASSWORD=...`

## Rollout order

1. `DISABLED`
2. `SHADOW`
3. `PERCENT=10`
4. `PERCENT=25`
5. `PERCENT=50`
6. `FULL`

Jangan lompat langsung ke `FULL`.

## Minimum observation per stage

- `SHADOW`: minimum 1 jam trafik normal
- `PERCENT=10`: minimum 30 menit trafik normal
- `PERCENT=25`: minimum 30 menit trafik normal
- `PERCENT=50`: minimum 30 menit trafik normal
- `FULL`: hanya setelah semua tahap sebelumnya stabil

## What to watch

- frontend production tetap normal
- `listing-query.proxy event=remote-success`
- `listing-query.proxy event=fallback`
- `listing-query.proxy event=status-mismatch`
- `listing-query.proxy event=payload-mismatch`
- `listing-query.service event=request-complete`

## Safe to continue when

- healthcheck stabil
- tidak ada mismatch yang tidak bisa dijelaskan
- fallback tidak muncul di traffic normal
- kategori dan listing detail parity stabil
- auth dan wallet flow tetap normal

## Rollback immediately when

- healthcheck service baru down/flapping
- fallback tinggi pada traffic normal
- ada mismatch konsisten
- frontend menerima error listing read

## Rollback

Set:

```bash
LISTING_QUERY_SERVICE_ROLLOUT_MODE=DISABLED
```

Atau:

```bash
LISTING_QUERY_SERVICE_ENABLED=false
```

Lalu restart monolith.
