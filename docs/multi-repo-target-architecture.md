# Multi-Repo Target Architecture

Audit ini dibuat dari branch `feat/auction-query-rollout`.

## 1. Audit struktur project saat ini

### Kondisi nyata codebase

- Root repo masih Spring Boot monolith utama.
- Repo ini sudah menjadi Gradle multi-module, bukan monolith murni.
- Service yang sudah benar-benar ada:
  - `backend` monolith utama
  - `auction-query-service` microservice read-only
  - `listing-query-service` microservice read-only kedua, masih belum selengkap `auction-query-service`

### Struktur yang sekarang

```text
backend/
├── src/main/java/id/ac/ui/cs/advprog/backend
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   ├── security
│   └── service
├── auction-query-service/
├── listing-query-service/
├── deployment/
└── docs/
```

### Modul/domain yang saat ini bercampur di monolith

- Auth dan identity
- User account
- Listing/catalog
- Auction command
- Auction query
- Bid history/query
- Wallet/balance/transaction
- Event persistence (`auction_event`)

### Coupling yang paling penting

- `AuctionService` tergantung pada:
  - listing write/read validation
  - wallet hold/release/capture
  - user repository
  - bid repository
  - auction event publisher
- `AuthService` masih membuat wallet saat register.
- Wallet state masih tersebar di dua tempat:
  - `app_user.available_balance / held_balance`
  - `wallet.balance`
- `PublicUserController` masih mengambil seller profile dari `ListingService`, jadi boundary user belum berdiri sendiri.
- Root monolith sudah berfungsi sebagai compatibility gateway untuk `auction-query-service` dan `listing-query-service`.

### Kekuatan struktur yang sudah ada

- Sudah ada pola rollout `DISABLED / SHADOW / PERCENT / FULL`.
- Sudah ada fallback fail-open dari monolith ke query service.
- Sudah ada dua calon service yang bisa dipisah paling cepat.
- Build multi-module saat ini valid dan test suite lulus.

### Kekurangan yang perlu ditangani sebelum ekstraksi berat

- Root `.env` masih menyimpan credential sensitif plaintext.
- Monolith masih memakai `spring.jpa.hibernate.ddl-auto=update`.
- Belum ada contract event broker; event baru disimpan ke tabel `auction_event`.
- `listing-query-service` belum punya `Dockerfile`.
- Belum ada `docker-compose` lokal yang menjalankan semua service target.

## 2. Module/domain yang cocok menjadi service

Urutan domain yang paling masuk akal dari kondisi sekarang:

1. `auction-query-service`
2. `listing-query-service`
3. `seller-profile-service` atau digabung dulu ke `listing-query-service`
4. `auction-command-service`
5. `wallet-service`
6. `auth-service`
7. `notification-service`

## 3. Service boundary yang tepat

### Boundary yang direkomendasikan

#### A. `bidmart-gateway-monolith`

Peran sementara:

- tetap expose `/api/*`
- routing ke service baru
- fallback dan shadow comparison
- host command flow lama yang belum diekstrak

Tanggung jawab:

- compatibility layer
- auth validation sementara
- proxy rollout

Catatan:

- Ini bukan target jangka panjang ideal, tetapi penting sebagai strangler facade.

#### B. `auction-query-service`

Tanggung jawab:

- baca daftar auction
- baca detail auction
- baca riwayat bid

Data yang boleh dimiliki:

- read model auction
- read model bid
- snapshot listing minimum
- snapshot bidder/seller minimum bila perlu

Boundary:

- read-only
- tidak menangani create/activate/place-bid/close

#### C. `listing-query-service`

Tanggung jawab:

- baca public listing catalog
- baca listing detail
- baca kategori listing
- baca category tree
- baca public seller summary ringan

Boundary:

- read-only
- tidak menangani create/update/delete listing
- tidak menangani ownership write flow

Catatan:

- `GET /api/users/{id}/public-profile` sebaiknya tahap awal tetap berada di service ini, bukan dibuat service terpisah dulu.

#### D. `listing-command-service`

Tanggung jawab:

- create listing
- update listing
- cancel listing
- validate listing for bid

Boundary:

- source of truth listing
- publish event listing changed
- tidak mengelola auction lifecycle

Catatan:

- Endpoint validation untuk bid boleh sementara tetap di sini karena auction command membutuhkannya.

#### E. `auction-command-service`

Tanggung jawab:

- create auction
- activate auction
- close auction

Boundary:

- source of truth auction lifecycle
- publish auction events
- tidak menjadi source of truth saldo

Catatan:

- `place bid` jangan dipisah ke service tersendiri dulu. Di codebase sekarang ia terlalu menempel ke auction dan wallet consistency.

#### F. `wallet-service`

Tanggung jawab:

- available balance
- held balance
- hold
- release
- capture
- top-up ledger
- transaction history

Boundary:

- source of truth saldo tunggal
- wajib menggantikan field saldo di `app_user`

Catatan:

- Ini harus datang setelah contract command service cukup stabil.

#### G. `auth-service`

Tanggung jawab:

- register
- login
- token issue
- user credential
- role

Boundary:

- source of truth identity dan credential
- bukan source of truth wallet

Catatan:

- `GET /api/auth/me` bisa menggabungkan token claims dengan lookup user ringan.

#### H. `notification-service`

Tanggung jawab:

- consume domain event
- kirim email / system notification

Boundary:

- side effect async
- tidak melayani transactional core flow

## 4. Repo yang perlu dibuat

Target repo final yang pragmatis:

1. `bidmart-gateway`
2. `bidmart-auction-query-service`
3. `bidmart-listing-query-service`
4. `bidmart-listing-command-service`
5. `bidmart-auction-command-service`
6. `bidmart-wallet-service`
7. `bidmart-auth-service`
8. `bidmart-notification-service`
9. `bidmart-local-dev` untuk `docker-compose`, env sample, dan local infra

### Kenapa tidak membuat terlalu banyak repo sekarang

- `user-service` belum punya boundary yang cukup kuat.
- `bid-service` belum cukup independen dari auction lifecycle dan wallet reservation.
- Memecah `public-profile-service` sebagai repo sendiri sekarang akan menambah hop tanpa mengurangi coupling berarti.

## 5. Skeleton final untuk masing-masing repo

### A. `bidmart-gateway`

```text
bidmart-gateway/
├── src/main/java/.../gateway
│   ├── config
│   ├── controller
│   ├── proxy
│   ├── security
│   └── rollout
├── src/main/resources
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### B. `bidmart-auction-query-service`

```text
bidmart-auction-query-service/
├── src/main/java/.../auctionquery
│   ├── config
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   └── service
├── src/main/resources
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### C. `bidmart-listing-query-service`

```text
bidmart-listing-query-service/
├── src/main/java/.../listingquery
│   ├── config
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   └── service
├── src/main/resources
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### D. `bidmart-listing-command-service`

```text
bidmart-listing-command-service/
├── src/main/java/.../listingcommand
│   ├── config
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   ├── service
│   ├── event
│   └── client
├── src/main/resources
├── db/migration
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### E. `bidmart-auction-command-service`

```text
bidmart-auction-command-service/
├── src/main/java/.../auctioncommand
│   ├── config
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   ├── service
│   ├── event
│   └── client
├── src/main/resources
├── db/migration
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### F. `bidmart-wallet-service`

```text
bidmart-wallet-service/
├── src/main/java/.../wallet
│   ├── config
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   ├── service
│   ├── event
│   └── api
├── src/main/resources
├── db/migration
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### G. `bidmart-auth-service`

```text
bidmart-auth-service/
├── src/main/java/.../auth
│   ├── config
│   ├── controller
│   ├── dto
│   ├── model
│   ├── repository
│   ├── security
│   ├── service
│   └── event
├── src/main/resources
├── db/migration
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### H. `bidmart-notification-service`

```text
bidmart-notification-service/
├── src/main/java/.../notification
│   ├── config
│   ├── consumer
│   ├── template
│   └── service
├── src/main/resources
├── deployment
├── Dockerfile
├── build.gradle
└── README.md
```

### I. `bidmart-local-dev`

```text
bidmart-local-dev/
├── docker-compose.yml
├── .env.example
├── postgres/
│   └── init/
├── rabbitmq/
├── grafana/
├── prometheus/
└── README.md
```

## 6. Endpoint/API tiap service

### `bidmart-gateway`

- `GET /api/auctions*` proxy ke `auction-query-service`
- `GET /api/listings*` proxy ke `listing-query-service`
- `POST /api/listings*` proxy ke `listing-command-service` saat siap
- `POST /api/auctions*` proxy ke `auction-command-service` saat siap
- `GET /api/wallet*` proxy ke `wallet-service` saat siap
- `POST /api/auth/*` proxy ke `auth-service` saat siap

### `bidmart-auction-query-service`

- `GET /api/auctions`
- `GET /api/auctions/{auctionId}`
- `GET /api/auctions/{auctionId}/bids`
- `GET /actuator/health`

### `bidmart-listing-query-service`

- `GET /api/listings`
- `GET /api/listings/{listingId}`
- `GET /api/listings/categories`
- `GET /api/listings/categories/tree`
- `GET /api/users/{userId}/public-profile`
- `GET /actuator/health`

### `bidmart-listing-command-service`

- `POST /api/listings`
- `PUT /api/listings/{listingId}`
- `DELETE /api/listings/{listingId}`
- `GET /internal/listings/{listingId}/validation`
- `GET /internal/listings/{listingId}`
- `GET /actuator/health`

### `bidmart-auction-command-service`

- `POST /api/auctions`
- `POST /api/auctions/{auctionId}/activate`
- `POST /api/auctions/{auctionId}/bids`
- `POST /api/auctions/{auctionId}/close`
- `GET /internal/auctions/{auctionId}`
- `GET /actuator/health`

### `bidmart-wallet-service`

- `GET /api/wallet/balance`
- `POST /api/wallet/topup`
- `GET /api/wallet/transactions`
- `POST /internal/wallet/holds`
- `POST /internal/wallet/releases`
- `POST /internal/wallet/captures`
- `GET /internal/wallet/accounts/{userId}`
- `GET /actuator/health`

### `bidmart-auth-service`

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /internal/users/{userId}`
- `POST /internal/tokens/introspect`
- `GET /actuator/health`

### `bidmart-notification-service`

- `POST /internal/notifications/email`
- `GET /actuator/health`

## 7. Database/schema tiap service

### Prinsip

- Query service boleh mulai dari replica/read-only schema dulu.
- Command/core service baru boleh punya DB sendiri jika source of truth sudah jelas.
- Jangan memaksa database-per-service penuh di phase pertama bila itu hanya memindahkan coupling dari code ke data sync.

### Target database ownership

#### `auction-query-service`

Opsi transisi:

- Phase awal: read-only ke DB bersama
- Phase lanjut: schema sendiri hasil projection

Tabel target projection:

- `auction_view`
- `auction_bid_view`
- `auction_listing_snapshot`

#### `listing-query-service`

Opsi transisi:

- Phase awal: read-only ke DB bersama
- Phase lanjut: schema sendiri hasil projection

Tabel target projection:

- `listing_view`
- `listing_category_view`
- `seller_profile_view`

#### `listing-command-service`

Schema target:

- `listing`
- `listing_category_ref` bila mau jadi table reference
- `listing_event`

#### `auction-command-service`

Schema target:

- `auction`
- `bid`
- `auction_event` atau `outbox_event`

#### `wallet-service`

Schema target:

- `wallet_account`
- `wallet_hold`
- `wallet_transaction`

Catatan:

- `available_balance` dan `held_balance` harus dipindahkan dari `app_user` ke wallet domain.

#### `auth-service`

Schema target:

- `app_user`
- `user_credential`
- `user_role_history` bila diperlukan

Catatan:

- Jika ingin sederhana, `app_user` dan credential bisa tetap satu table dulu.

## 8. Komunikasi antar-service

### Tahap transisi awal

- Sinkron HTTP melalui gateway/proxy
- Query service dibaca lewat monolith gateway
- Command service saling panggil via internal HTTP hanya untuk kebutuhan minimum

### Tahap menengah

- Tambah outbox event
- Tambah broker tunggal, misalnya RabbitMQ atau Kafka

Event awal yang perlu dipublikasikan:

- `ListingCreated`
- `ListingUpdated`
- `ListingCancelled`
- `AuctionActivated`
- `BidPlaced`
- `AuctionClosed`
- `AuctionResolved`
- `WalletFundsHeld`
- `WalletFundsReleased`
- `WalletFundsCaptured`
- `UserRegistered`

### Aturan komunikasi

- Query service jangan memanggil command service untuk request normal.
- Gunakan event untuk projection query.
- Gunakan HTTP sinkron hanya pada transaksi yang benar-benar butuh jawaban langsung.
- Wallet harus idempotent untuk hold/release/capture.

## 9. Migration plan bertahap

### Phase 0 - stabilisasi fondasi

- pertahankan monolith sebagai entrypoint
- rotasi semua secret yang sudah terlanjur tersimpan di repo
- hentikan ketergantungan pada `.env` yang berisi credential nyata
- pindahkan ke `.env.example`
- siapkan Flyway/Liquibase untuk service baru sebelum write-service dipisah

### Phase 1 - selesaikan query service

- finalkan `auction-query-service`
- tambahkan `Dockerfile` untuk `listing-query-service`
- pastikan `listing-query-service` mengikuti pola rollout yang sama
- rollout `auction-query-service` dan `listing-query-service` lewat gateway existing

Hasil phase:

- dua read-side service berjalan independen
- frontend tetap tidak berubah

### Phase 2 - ekstrak `listing-command-service`

- pindahkan write endpoint listing dari monolith
- monolith tetap proxy endpoint yang sama
- pertahankan DB bersama dulu bila perlu
- publikasikan event listing untuk query projection

### Phase 3 - ekstrak `auction-command-service`

- pindahkan create/activate/close auction
- `place bid` bisa ikut bila contract wallet sudah siap
- jika belum, keep bid path sementara di gateway/legacy sampai wallet API matang

### Phase 4 - ekstrak `wallet-service`

- satukan source of truth saldo
- migrasikan logic hold/release/capture
- hapus ketergantungan saldo dari `app_user`
- baru setelah itu arahkan auction command ke wallet API internal

### Phase 5 - ekstrak `auth-service`

- pindahkan register/login/token issue
- gateway cukup validate token atau introspect
- putus coupling register -> create wallet sinkron

### Phase 6 - ekstrak `notification-service`

- kirim notifikasi berdasarkan event
- jangan taruh notifikasi di critical synchronous request

### Phase 7 - pisah database sepenuhnya

- query service ke projection DB sendiri
- command service ke DB masing-masing
- matikan akses lintas schema yang bukan miliknya

## 10. Urutan file/folder yang harus dipindahkan dulu

### Langkah 1: pindahkan yang sudah hampir berdiri sendiri

Pindah dulu ke repo baru tanpa mengubah kontrak besar:

1. `auction-query-service/**`
2. `listing-query-service/**`

Alasan:

- sudah modular
- blast radius rendah
- endpoint read-only

### Langkah 2: pindahkan adapter gateway yang terkait query rollout

Tetap di repo gateway/legacy:

- `src/main/java/.../config/AuctionQueryServiceProperties.java`
- `src/main/java/.../config/ListingQueryServiceProperties.java`
- `src/main/java/.../service/AuctionReadGateway.java`
- `src/main/java/.../service/ProxyingAuctionReadGateway.java`
- `src/main/java/.../service/ListingReadGateway.java`
- `src/main/java/.../service/ProxyingListingReadGateway.java`

Ini jangan dipindah ke query repo karena itu concern gateway.

### Langkah 3: ekstrak listing write

Kandidat pindah ke `listing-command-service`:

- `controller/ListingController.java` bagian write
- `dto/ListingCreateRequest.java`
- `dto/ListingUpdateRequest.java`
- `dto/ListingBidValidationResponse.java`
- `model/Listing.java`
- `model/ListingCategory.java`
- `model/ListingStatus.java`
- `repository/ListingRepository.java`
- bagian write dari `service/ListingService.java`

Catatan:

- `ListingController` sekarang campur read dan write. Pecah dulu menjadi controller read dan controller write sebelum dipindahkan repo.

### Langkah 4: ekstrak auction command

Kandidat pindah ke `auction-command-service`:

- `controller/AuctionController.java` bagian write
- `dto/AuctionCreateRequest.java`
- `dto/BidPlaceRequest.java`
- `model/Auction.java`
- `model/Bid.java`
- `model/AuctionStatus.java`
- `repository/AuctionRepository.java`
- `repository/BidRepository.java`
- `repository/AuctionEventRepository.java`
- `service/AuctionService.java`
- `service/AuctionEventPublisher.java`
- `service/DatabaseAuctionEventPublisher.java`

Catatan:

- Endpoint read auction tetap di gateway/query path, jangan ikut dipindah lagi.

### Langkah 5: ekstrak wallet

Kandidat pindah ke `wallet-service`:

- `controller/WalletController.java`
- `dto/TopUpRequest.java`
- `dto/TransactionResponse.java`
- `dto/WalletResponse.java`
- `model/Wallet.java`
- `model/WalletTransaction.java`
- `repository/WalletRepository.java`
- `repository/WalletTransactionRepository.java`
- `service/WalletService.java`
- `service/WalletGateway.java`
- `service/LocalWalletGateway.java`

Catatan:

- `LocalWalletGateway` nanti berubah menjadi HTTP client/adaptor ke wallet-service, bukan logic domain lokal.

### Langkah 6: ekstrak auth

Kandidat pindah ke `auth-service`:

- `controller/AuthController.java`
- `dto/LoginRequest.java`
- `dto/LoginResponse.java`
- `dto/RegisterRequest.java`
- `dto/RegisterResponse.java`
- `dto/AuthResponse.java`
- `dto/UserSummary.java`
- `model/User.java`
- `model/Role.java`
- `repository/UserRepository.java`
- `security/**`
- `service/AuthService.java`

Catatan:

- Lakukan setelah wallet tidak lagi dibuat sinkron dari auth register.

## 11. Risiko yang harus dihindari agar tidak over-engineering

### Risiko 1

Memecah terlalu banyak service sekaligus.

Hindari:

- membuat `bid-service`, `user-service`, dan `profile-service` terpisah dari awal

### Risiko 2

Memaksa database-per-service penuh sebelum event dan ownership matang.

Hindari:

- memindahkan tabel ke DB berbeda tetapi masih query silang langsung

### Risiko 3

Mengubah kontrak frontend bersamaan dengan migrasi backend.

Hindari:

- rename endpoint publik di awal

### Risiko 4

Memisahkan wallet sebelum source of truth saldo tunggal.

Hindari:

- membiarkan `app_user.availableBalance`, `app_user.heldBalance`, dan `wallet.balance` tetap hidup lama setelah wallet-service aktif

### Risiko 5

Menjadikan broker sebagai syarat awal semua service.

Hindari:

- memasang Kafka hanya karena microservices

Untuk tahap sekarang, RabbitMQ pun belum wajib sampai command service mulai publish projection event.

### Risiko 6

Membuat shared library domain terlalu gemuk.

Hindari:

- satu repo `bidmart-common` yang berisi entity JPA lintas service

Jika perlu shared code, batasi ke:

- DTO contract kecil
- auth token utility
- observability helper

### Risiko 7

Menghapus monolith terlalu cepat.

Hindari:

- mematikan fallback sebelum parity query service terukur

## Rekomendasi praktis paling dekat

Urutan kerja berikut paling realistis dari kondisi branch ini:

1. Jadikan `auction-query-service` repo terpisah pertama.
2. Lengkapi `listing-query-service` dengan `Dockerfile`, parity test, lalu jadikan repo terpisah kedua.
3. Pecah `ListingController` menjadi read/write concern di monolith supaya ekstraksi listing command lebih mudah.
4. Pecah `AuctionController` read/write concern secara eksplisit.
5. Satukan model saldo sebelum wallet diekstrak.
6. Baru setelah itu pecah command service.
