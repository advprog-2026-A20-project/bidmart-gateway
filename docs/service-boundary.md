# Service Boundary - bidmart-gateway

`bidmart-gateway` berperan sebagai:

1. pintu masuk backend utama selama migrasi,
2. host untuk modul legacy yang belum dipisahkan,
3. strangler facade untuk meneruskan request ke service baru.

## Batas Tanggung Jawab Gateway

- Terminasi request dari client internal/external.
- Routing request ke service domain yang sudah dipisah.
- Menjaga kompatibilitas endpoint legacy yang masih dipakai.
- Menjadi titik transisi sampai semua domain keluar dari monolith.

## Yang Tidak Menjadi Tanggung Jawab Gateway

- UI/frontend (dipindah ke repo `bidmart-frontend`).
- Implementasi domain logic final milik service terpisah.
