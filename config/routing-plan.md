# Routing Plan BidMart Gateway

## Tabel Routing Aktif

| Path | Target Service | Status |
|---|---|---|
| `/api/auth/**` | `bidmart-auth-service` | aktif |
| `/api/wallet/**` | `bidmart-wallet-service` | aktif |
| `/api/auctions/**` (GET) | `bidmart-auction-query-service` | aktif |
| `/api/auctions/**` (POST command) | `bidmart-bidding-command-service` | aktif |
| `/api/listings/**` (GET) | `bidmart-listing-query-service` | aktif |
| `/api/listings/**` (POST/PUT/DELETE) | `bidmart-listing-query-service` | aktif |
| `/api/users/**` | `bidmart-listing-query-service` public profile endpoint | aktif |

## Env yang Dipakai

- `AUTH_SERVICE_BASE_URL`
- `WALLET_SERVICE_BASE_URL`
- `BIDDING_COMMAND_SERVICE_BASE_URL`
- `AUCTION_QUERY_SERVICE_BASE_URL`
- `LISTING_QUERY_SERVICE_BASE_URL`
