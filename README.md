# bidmart-gateway

`bidmart-gateway` adalah repository untuk **gateway backend** sekaligus **legacy monolith strangler facade** pada migrasi BidMart dari monolith ke microservice multi-repo.

## Tujuan Repository

- Menjadi pintu masuk backend selama fase migrasi.
- Menampung bagian legacy backend yang belum dipisahkan.
- Meneruskan request ke service baru yang sudah dipisahkan secara bertahap.

## Status Inisialisasi

> Catatan: Analisis/penyalinan langsung dari repo sumber `feat/auction-query-rollout` **belum dapat diverifikasi di environment ini** karena akses network ke GitHub tidak tersedia saat eksekusi.

## Struktur Awal

- `config/routing-plan.md` — rencana routing strangler sementara.
- `docs/service-boundary.md` — boundary dan tanggung jawab service gateway.
- `src/main/java` — placeholder source backend gateway/legacy.
- `src/main/resources` — placeholder konfigurasi aplikasi.

## Cara Menjalankan Lokal (sementara)

Karena source code backend legacy belum tersalin di environment ini, langkah run final akan mengikuti stack asli dari repo sumber (diasumsikan Java/Spring berdasarkan struktur migrasi yang disiapkan).

Contoh (asumsi Spring Boot):

```bash
./gradlew bootRun
```

atau

```bash
mvn spring-boot:run
```

## Instruksi Test (sementara)

```bash
./gradlew test
```

atau

```bash
mvn test
```

## Dependency ke Service Lain

Gateway akan bergantung pada endpoint service berikut:

- `bidmart-auth-service`
- `bidmart-listing-query-service`
- `bidmart-auction-query-service`
- `bidmart-bidding-command-service`
- `bidmart-wallet-service`
- `bidmart-notification-service`

## Routing Table Sementara

| Path | Target Service | Fallback |
|---|---|---|
| `/api/auth/**` | `bidmart-auth-service` | legacy auth sementara |
| `/api/listings/**` | `bidmart-listing-query-service` | legacy listings sementara |
| `/api/auctions/**` | `bidmart-auction-query-service` | legacy auctions sementara |
| `/api/bids/**` | `bidmart-bidding-command-service` | legacy bids sementara |
| `/api/wallets/**` | `bidmart-wallet-service` | legacy wallets sementara |
| `/api/notifications/**` | `bidmart-notification-service` | legacy notifications sementara |

Detail ada di `config/routing-plan.md`.

## Bagian Legacy yang Masih Ada (sementara)

Pada tahap ini gateway diposisikan tetap menampung modul legacy yang belum dipisahkan. Daftar modul konkret perlu dikonfirmasi setelah sinkronisasi dari branch sumber berhasil.

## Rencana Pemindahan Bertahap

1. Sinkronisasi backend legacy dari branch sumber.
2. Pisahkan endpoint domain per bounded context.
3. Ubah endpoint gateway menjadi forward/proxy ke service domain.
4. Hapus kode legacy setelah setiap service stabil di production-like environment.
