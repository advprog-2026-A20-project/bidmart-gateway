# Monolith to Microservices

Dokumen ini merangkum perubahan yang sudah dilakukan sampai tahap sekarang. Fokusnya hanya perubahan nyata di codebase, bukan rencana jangka panjang penuh.

## Kondisi saat ini

- Frontend tetap memakai `/api/*`
- Monolith Spring Boot tetap menjadi backend utama
- `auction-query-service` sudah dibuat sebagai microservice pertama untuk read-only auction
- Endpoint lama belum dihapus
- Database production belum dipecah dan belum ada migration schema yang breaking

## Tujuan perubahan

- Memecah backend bertahap tanpa memutus aplikasi yang sudah live
- Menjaga kontrak API lama supaya frontend tidak perlu ikut berubah besar
- Menambah service baru di belakang monolith, lalu memindahkan traffic secara bertahap

## Perubahan yang sudah dilakukan

### 1. Menambah service baru untuk auction read

Service baru:

- `auction-query-service`

Endpoint yang dilayani:

- `GET /api/auctions`
- `GET /api/auctions/{id}`
- `GET /api/auctions/{id}/bids`

Catatan:

- Service ini hanya read-only
- Service ini masih membaca dari database yang sama
- Service ini belum menulis data apa pun

## 2. Monolith tetap menjadi gateway kompatibilitas

Endpoint lama tetap diakses dari monolith:

- `GET /api/auctions`
- `GET /api/auctions/{id}`
- `GET /api/auctions/{id}/bids`

Monolith sekarang punya gateway/proxy internal untuk menentukan apakah request:

- dilayani lokal oleh monolith
- dibaca dari `auction-query-service`
- dibandingkan dulu di mode shadow
- di-fallback kembali ke monolith jika service baru gagal

## 3. Menambah rollout mode

Rollout mode yang sudah tersedia:

- `DISABLED`
- `SHADOW`
- `PERCENT`
- `FULL`

Arti singkat:

- `DISABLED`: semua request tetap ke monolith
- `SHADOW`: response tetap dari monolith, service baru hanya dibandingkan di belakang
- `PERCENT`: sebagian request dialihkan ke service baru
- `FULL`: semua request auction read dialihkan ke service baru

## 4. Menambah fallback aman

Jika `auction-query-service` error, timeout, atau down:

- request auction read bisa kembali dilayani monolith
- frontend tetap memakai endpoint lama
- tidak perlu rollback database

## 5. Menambah observability dasar

Logging dasar sudah ditambahkan untuk:

- request yang diproxy ke service baru
- fallback ke monolith
- mismatch status/payload saat shadow mode
- request log di `auction-query-service`

Tujuannya supaya rollout bisa diverifikasi sebelum traffic dinaikkan.

## 6. Menambah test untuk jalur yang terdampak

Test yang sudah ditambah mencakup:

- integration test endpoint auction read
- test fallback proxy
- test timeout/service down
- regression test wallet endpoint yang masih berjalan di monolith

## Yang belum diubah

- Frontend tidak diubah
- Auth belum dipisah
- Wallet belum dipisah
- Auction command belum dipisah
- Schema database belum dipecah
- Endpoint lama belum dihapus

## Urutan perubahan sampai sekarang

1. Audit dependency frontend ke backend
2. Pilih domain paling aman untuk diekstrak: auction read
3. Buat `auction-query-service` sebagai service read-only
4. Pertahankan endpoint lama di monolith
5. Tambahkan gateway/proxy di monolith
6. Tambahkan fallback ke monolith
7. Tambahkan rollout mode `DISABLED/SHADOW/PERCENT/FULL`
8. Tambahkan logging dan test
9. Siapkan staging dan playbook rollout production

## Posisi proyek saat ini

Status sekarang masih transisi bertahap, belum fully microservice.

Struktur operasional saat ini:

- Monolith masih wajib ada
- `auction-query-service` sudah siap menjadi service pertama yang menerima traffic bertahap
- Frontend masih aman karena tetap memakai kontrak API lama

## Langkah berikutnya setelah ini

- Jalankan rollout production `auction-query-service` bertahap mulai dari `SHADOW`
- Pastikan parity response stabil
- Naikkan traffic sedikit demi sedikit
- Evaluasi domain berikutnya hanya setelah auction read stabil
