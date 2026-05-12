# Service Boundary - bidmart-gateway

Gateway berfungsi sebagai entrypoint API dan router antar microservice.

## Tanggung Jawab Gateway

- terminasi request client
- routing/proxy ke service domain
- delegasi read endpoint ke query services
- menjaga contract endpoint publik yang dipakai frontend

## Bukan Tanggung Jawab Gateway

- source of truth auth
- source of truth wallet
- source of truth auction/bidding command

## Mapping Domain Ownership

- auth ownership -> `bidmart-auth-service`
- wallet ownership -> `bidmart-wallet-service`
- auction/bid command ownership -> `bidmart-bidding-command-service`
- listing/auction query ownership -> `bidmart-listing-query-service`, `bidmart-auction-query-service`

## Status Cleanup

Runtime file/bean legacy monolith untuk auth, wallet, auction command, bidding command sudah dihapus dari gateway.
