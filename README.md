# BidMart Gateway

`bidmart-gateway` adalah API gateway/BFF untuk frontend BidMart.

Status saat ini: gateway adalah thin routing/proxy layer untuk auth, wallet, auction command, dan delegasi read ke query services.

## Routing Utama

- `POST /api/auth/register|login`, `GET /api/auth/me` -> `bidmart-auth-service`
- `GET/POST /api/wallet/*` -> `bidmart-wallet-service`
- `POST /api/auctions`, `POST /api/auctions/{id}/activate|bids|close` -> `bidmart-bidding-command-service`
- `GET /api/auctions*` -> `bidmart-auction-query-service`
- `GET /api/listings*` -> `bidmart-listing-query-service`
- `GET /api/users/{userId}/public-profile` -> gateway local endpoint

## Health

- `GET /actuator/health`

## Environment

Lihat `.env.example`.

Variabel penting:

- `AUTH_SERVICE_BASE_URL`
- `WALLET_SERVICE_BASE_URL`
- `BIDDING_COMMAND_SERVICE_BASE_URL`
- `AUCTION_QUERY_SERVICE_BASE_URL`
- `LISTING_QUERY_SERVICE_BASE_URL`

## Local Run

```bash
cp .env.example .env
./gradlew bootRun
```

Default port: `8080`.

## Test

```bash
./gradlew test
```

## Docker

```bash
docker build -t bidmart-gateway .
docker run --env-file .env -p 8080:8080 bidmart-gateway
```
