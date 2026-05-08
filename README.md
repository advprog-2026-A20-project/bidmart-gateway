# BidMart Gateway

Repository ini berisi gateway BidMart sekaligus legacy monolith adapter selama migrasi microservice. Kode ini berasal dari `backend/` pada repository lama `Bidmart` branch `feat/auction-query-rollout`.

## Service Boundary

Gateway bertanggung jawab untuk:

- Menjaga kontrak publik `/api/*` agar frontend tidak perlu berubah saat migrasi.
- Menjalankan command flow legacy yang belum aman dipisah.
- Melakukan routing bertahap ke read-side service seperti auction query dan listing query.
- Menyediakan fallback ke logic lokal saat service baru belum stabil.

Gateway tidak menjadi target akhir untuk ownership domain. Logic auth, listing, bidding, wallet, dan notification akan dipindahkan bertahap ke repo service masing-masing.

## Isi Repo

- `src/main/java/.../controller`: controller kompatibilitas endpoint publik.
- `src/main/java/.../service`: legacy service dan gateway adapter.
- `src/main/java/.../security`: JWT filter dan konfigurasi security sementara.
- `src/main/resources`: konfigurasi Spring Boot.
- `deployment`: contoh konfigurasi deployment dari repo lama.
- `docs`: catatan roadmap migrasi.

## Run Lokal

```bash
./gradlew bootRunLocal
```

Atau jalankan dengan PostgreSQL:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bidmart \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
JWT_SECRET=local-dev-secret-please-change-32-chars \
./gradlew bootRun
```

## Test

```bash
./gradlew test
```

## Dependency Service Lain

Gateway dapat mem-proxy read request ke:

- `bidmart-auction-query-service`
- `bidmart-listing-query-service`

Untuk sementara command flow masih lokal. Setelah strangler berjalan, POST command akan diarahkan ke service command terkait.

## Catatan Migrasi

- Jangan commit `.env`, credential, token, private key, `build/`, `target/`, atau file IDE lokal.
- Query service boleh fail-open ke logic lokal selama rollout.
- Endpoint publik harus tetap kompatibel sampai gateway adapter diganti penuh.
