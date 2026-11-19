## KayitPlugin v2.0.0

Tamamen yeniden yazılmış sürüm. **Tek JAR: Spigot / Paper / Purpur / Leaf 1.16.x – 1.21.x, Java 8 – 21.**
Bu sürüm GitHub Actions'ta gerçek Paper sunucularında (1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.1, 1.21.4)
botlarla uçtan uca test edildi; konsolda sıfır hata.

### Düzeltilen hatalar
- **E-postaya kod gitmiyordu** → kökten çözüldü. Relocate edilen JavaMail'in sağlayıcı/mailcap dosyaları eski sınıf adlarını gösteriyordu; SMTP artık doğrudan bağlanıyor, içerik DataSource ile gönderiliyor. `/kod gonder` de çalışıyor.
- **`UnsatisfiedLinkError` (SQLite)** → SQLite/MySQL sürücüleri artık JAR'a gömülmüyor, sunucunun kendi sürücüsü kullanılıyor.
- Şifre hashleme ve veritabanı işlemleri ana thread'i donduruyordu → tamamen arka plana alındı.
- `setWalkSpeed(0)` ile dondurma, sunucu çökerse oyuncuyu kalıcı yavaşlatıyordu → kaldırıldı, hareket olay tabanlı engelleniyor.
- Giriş yapmamış oyuncu havada kalınca "Flying is not enabled" ile atılıyordu → düşmeye izin veriliyor.

### Yeni
- **Dil desteği (tr / en)**: mesajlar, e-posta şablonları **ve komut adları** seçilen dilde (`language: en` → `/register`, `/login`, `/alts`…).
- **Şifre uzunluk sınırı config'den**: `password.min-length` / `password.max-length` (+ kolay şifre listesi, kullanıcı adı yasağı).
- **IP kayıt limiti** ve **aynı IP eş zamanlı oturum limiti**.
- **`/hesaplar <oyuncu>`**: tüm IP'lerdeki alt hesaplar, aktiflik ("2 gün önce aktifti"), ban sebebi, tarihi, bitişi ve banı atan yetkili — sadece komutu yazana.
- **Ban okuma**: Vanilla, EssentialsX, LiteBans (API/MySQL), AdvancedBan (API/MySQL) + diğer eklentiler için komut izleyici. 60 sn'de bir ve her ban/unban'dan ~3 sn sonra yenilenir.
- **Banlı alt hesap uyarısı**: oyuncu girince/kayıt olunca yetkililere tıklanabilir uyarı.
- `/sifredegistir`, `/kayitsistemi yenile | bilgi | testmail`.
- Giriş zaman aşımı, yanlış şifre limiti, oyundaki hesabı atma koruması, oturum hatırlama, reload sonrası oturum koruma, körlük/başlık, Discord webhook.
- MySQL / MariaDB desteği, v1.x veritabanından otomatik taşıma.

### Kurulum
1. `KayitPlugin-2.0.0.jar` dosyasını `plugins/` klasörüne atın (eski 1.x JAR'ını silin).
2. `plugins/KayitPlugin/config.yml` → `email.username` ve `email.password` (Gmail **uygulama şifresi**).
3. `/kayitsistemi yenile` → `/kayitsistemi testmail adresiniz@mail.com`

Ayrıntılar: [README](https://github.com/jesthub25/Kay-t-Sistem#readme)

---

**English:** Full rewrite. One JAR for 1.16.x – 1.21.x (Java 8 – 21). Fixes email codes never being sent and the SQLite
`UnsatisfiedLinkError`; adds Turkish/English language files (including command names), configurable password length,
IP registration/session limits, `/alts` with ban reason/date/staff, ban reading from Vanilla/EssentialsX/LiteBans/AdvancedBan,
banned-alt staff alerts, MySQL/MariaDB and automatic migration from v1.x. End-to-end tested on real Paper servers.
