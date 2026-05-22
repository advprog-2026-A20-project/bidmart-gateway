# Microservices Roadmap

Dokumen ini adalah pegangan jangka panjang untuk memecah backend Bidmart dari monolith ke microservices secara bertahap. Fokusnya supaya tim tidak kehilangan arah selama transisi.

## Tujuan

- Backend dipecah bertahap tanpa memutus aplikasi yang sudah live
- Frontend tetap bisa memakai `/api/*` selama masa transisi
- Endpoint lama tetap kompatibel sampai replacement stabil
- Perubahan database harus additive dan backward-compatible
- Rollback harus selalu tersedia di setiap tahap

## Prinsip kerja

- Jangan pindah banyak domain sekaligus
- Mulai dari read-only flow dengan risiko paling kecil
- Monolith tetap menjadi gateway/adapter sampai service baru terbukti stabil
- Jangan ekstrak auth sebelum JWT/session contract jelas
- Jangan ekstrak wallet sebelum source of truth saldo disatukan
- Jangan hapus endpoint lama sebelum traffic dan parity sudah aman

## Kondisi saat ini

Sudah selesai:

- `auction-query-service` sudah dibuat
- Monolith tetap melayani endpoint lama `/api/auctions*`
- Monolith sudah punya rollout mode:
  - `DISABLED`
  - `SHADOW`
  - `PERCENT`
  - `FULL`
- Fallback ke monolith sudah tersedia
- Logging dasar untuk proxy, fallback, dan mismatch sudah ada

Belum selesai:

- Production rollout auction query belum benar-benar dijalankan
- Auth masih di monolith
- Wallet masih di monolith
- Listing, user profile, notification, dan write-side auction masih di monolith

## Arsitektur target

Service target jangka panjang:

- `gateway / legacy monolith adapter`
- `auth-service`
- `user-service`
- `listing-service`
- `auction-command-service`
- `auction-query-service`
- `bid-service`
- `wallet-payment-service`
- `notification-service`

Selama masa transisi:

- frontend tetap ke `/api/*`
- gateway bisa tetap di monolith existing
- routing ke service baru dilakukan di belakang gateway

## Urutan migrasi yang direkomendasikan

### Phase 1

`auction-query-service`

Scope:

- `GET /api/auctions`
- `GET /api/auctions/{id}`
- `GET /api/auctions/{id}/bids`

Alasan:

- read-only
- paling kecil risikonya
- sudah dipakai frontend
- bisa fail-open ke monolith

Status:

- sudah dibuat
- sedang disiapkan untuk rollout production bertahap

### Phase 2

`listing read / public catalog`

Scope awal:

- `GET /api/listings`
- `GET /api/listings/{id}`
- `GET /api/listings/categories/tree`
- endpoint public catalog lain yang tidak mengubah data

Alasan:

- dominan read-only
- tidak menyentuh saldo
- tidak bergantung kuat ke JWT write flow

Risiko:

- filter/sort/pagination harus tetap identik
- integrasi dengan auction view harus tetap konsisten

Rollback:

- route kembali ke monolith
- tidak perlu rollback DB jika masih read-only

### Phase 3

`user public profile`

Scope awal:

- `GET /api/users/{id}/public-profile`

Alasan:

- public read
- kontrak kecil
- blast radius rendah

Risiko:

- mapping data profile dan seller stats harus tetap sama

Rollback:

- route kembali ke monolith

### Phase 4

`notification-service`

Scope awal:

- email notification
- system notification async

Alasan:

- cocok dipisah sebagai side-effect service
- bisa dibuat async dan tidak mengubah kontrak utama dulu

Risiko:

- retry dan duplicate send
- butuh event/outbox yang rapi

Rollback:

- matikan consumer/publisher baru
- biarkan monolith tetap kirim notifikasi lama jika masih ada fallback

### Phase 5

`auction-command-service` dan `bid-service`

Scope awal:

- create auction
- activate auction
- place bid
- close auction

Alasan:

- setelah query side sudah stabil, baru write side dipisah

Risiko:

- menyentuh listing
- menyentuh wallet hold/release
- concurrency dan consistency lebih kompleks

Syarat sebelum mulai:

- contract event jelas
- locking/concurrency strategy jelas
- audit trail dan rollback operasional jelas

### Phase 6

`wallet-payment-service`

Scope:

- wallet balance
- top up
- hold/release/capture funds
- transaction ledger

Jangan mulai sebelum:

- source of truth saldo tunggal sudah ditentukan
- model `wallet.balance` vs `app_user.availableBalance/heldBalance` sudah disatukan

Ini domain berisiko paling tinggi.

### Phase 7

`auth-service`

Scope:

- login
- register
- token issue
- token validation/introspection bila perlu

Jangan mulai sebelum:

- JWT/session contract final
- gateway auth strategy final
- ownership/role checks di service lain sudah jelas

Ini juga domain berisiko tinggi.

## Data ownership target

Target ownership jangka panjang:

- `auth-service`
  - user credential
  - token/session metadata jika ada
- `user-service`
  - profile public/private
  - seller metadata
- `listing-service`
  - listing
  - category mapping
- `auction-command-service`
  - auction lifecycle
  - auction event write side
- `auction-query-service`
  - read model auction dan bid view
- `bid-service`
  - bid write flow dan bid history write side
- `wallet-payment-service`
  - wallet ledger
  - balance/hold state
- `notification-service`
  - notification log/outbox delivery status

Catatan:

- Pada tahap awal beberapa service masih boleh membaca shared DB
- Target akhirnya tiap service punya ownership jelas, bukan shared-write

## Kontrak routing selama transisi

Tetap pertahankan:

- frontend -> `/api/*`

Gateway bertugas:

- route request ke monolith atau service baru
- menjaga response contract tetap sama
- fallback ke monolith saat rollout belum stabil
- mencatat mismatch saat shadow mode

## Strategi database

Tahap awal:

- service baru boleh read dari DB yang sama
- jangan lakukan destructive schema migration
- semua perubahan schema harus additive

Tahap menengah:

- mulai pisahkan ownership tabel per domain
- tambahkan event/outbox untuk sinkronisasi bila perlu
- siapkan read model terpisah untuk query-heavy service

Tahap akhir:

- hilangkan shared-write antar service
- gateway tidak lagi tergantung pada logic monolith lama untuk domain yang sudah selesai dipindah

## Definition of done per phase

Setiap phase baru dianggap selesai jika:

- endpoint lama tetap kompatibel
- smoke test frontend lulus
- fallback teruji
- healthcheck service baru stabil
- mismatch log sudah dipahami atau nol
- rollback cukup lewat config/routing
- tidak ada breaking DB migration
- dokumentasi deploy dan rollback sudah ada

## Kondisi aman lanjut ke phase berikutnya

Lanjut hanya jika:

- traffic phase sebelumnya stabil
- error rate tidak naik
- fallback tidak sering muncul di traffic normal
- tim paham owner, contract, dan rollback domain berikutnya

## Kondisi untuk berhenti dulu

Jangan lanjut ekstraksi berikutnya jika:

- parity response belum stabil
- observability belum cukup
- ada domain coupling yang belum dipahami
- data ownership masih kabur
- rollback belum sederhana

## Apa yang perlu dijaga tim

- Jangan ubah kontrak endpoint frontend tanpa adapter
- Jangan merge ekstraksi domain berisiko tinggi bersamaan
- Jangan gabungkan wallet dan auction write refactor dalam satu phase
- Jangan hapus fallback terlalu cepat
- Selalu uji auth dan wallet meskipun domain yang dipindah bukan auth/wallet

## Ringkasan urutan pengerjaan

1. Selesaikan rollout `auction-query-service`
2. Ekstrak listing/public catalog read
3. Ekstrak user public profile read
4. Pisahkan notification/outbox flow
5. Pisahkan auction write dan bid write
6. Rapikan model wallet lalu ekstrak wallet/payment
7. Finalkan JWT/session contract lalu ekstrak auth
8. Kecilkan monolith sampai tinggal gateway/adapter dan fallback legacy
