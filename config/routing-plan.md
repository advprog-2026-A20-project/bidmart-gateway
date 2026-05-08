# Routing Plan Sementara BidMart Gateway

Dokumen ini menjelaskan rencana routing pada fase strangler pattern.

## Tabel Routing

| Path | Target Service Utama | Fallback Legacy | Status |
|---|---|---|---|
| `/api/auth/**` | `bidmart-auth-service` | Ya (sementara) | TODO implementasi proxy |
| `/api/listings/**` | `bidmart-listing-query-service` | Ya (sementara) | TODO implementasi proxy |
| `/api/auctions/**` | `bidmart-auction-query-service` | Ya (sementara) | TODO implementasi proxy |
| `/api/bids/**` | `bidmart-bidding-command-service` | Ya (sementara) | TODO implementasi proxy |
| `/api/wallets/**` | `bidmart-wallet-service` | Ya (sementara) | TODO implementasi proxy |
| `/api/notifications/**` | `bidmart-notification-service` | Ya (sementara) | TODO implementasi proxy |

## Strategi Implementasi Bertahap

1. Tambahkan konfigurasi base URL per service via environment variables.
2. Implementasikan reverse-proxy/forwarder per route pada gateway.
3. Aktifkan fallback ke handler legacy untuk endpoint yang belum siap.
4. Tambahkan observability (logging, tracing, metrics) per route service.
5. Setelah stabil, matikan fallback legacy endpoint per endpoint.

## Variabel Environment yang Disarankan

- `AUTH_SERVICE_BASE_URL`
- `LISTING_QUERY_SERVICE_BASE_URL`
- `AUCTION_QUERY_SERVICE_BASE_URL`
- `BIDDING_COMMAND_SERVICE_BASE_URL`
- `WALLET_SERVICE_BASE_URL`
- `NOTIFICATION_SERVICE_BASE_URL`
