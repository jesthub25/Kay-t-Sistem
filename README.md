# KayitPlugin v2 — AvenorCraft Kayıt / Giriş Sistemi

[![Build & Test](https://github.com/jesthub25/Kay-t-Sistem/actions/workflows/build.yml/badge.svg)](https://github.com/jesthub25/Kay-t-Sistem/actions/workflows/build.yml)

Kayıt/giriş, e-posta doğrulama, IP limitleri, **alt hesap tespiti** ve **ban takibi** yapan, tek JAR'lık hafif bir sistem.
Geliştirici: **Jestron** · *English below.*

| Destek | |
|---|---|
| Sunucu | Spigot, Paper, Purpur, Leaf ve fork'ları |
| Sürüm | **1.16.x → 1.21.x** (tek JAR) |
| Java | 8, 11, 16, 17, 21 |
| Veritabanı | SQLite (varsayılan), MySQL, MariaDB |
| Dil | Türkçe, English (komutlar dahil) |

Her sürüm GitHub Actions üzerinde **gerçek Paper sunucularında** botlarla uçtan uca test edilir
(1.16.5 · 1.17.1 · 1.18.2 · 1.19.4 · 1.20.4 · 1.20.6 · 1.21.1 · 1.21.4 · 1.21.8 · 1.21.11, ayrıca AdvancedBan ve EssentialsX ile):
kayıt, giriş, yanlış şifre, şifre limitleri, sohbet/komut engeli, **gerçek SMTP ile e-posta kodu** (kayıt, 2 adımlı giriş,
e-posta değiştirme, test e-postası), şifre değiştirme, ban → banlı alt hesap uyarısı, unban, `/hesaplar`, yönetici komutları,
dil değişimi (TR → EN, komut adları dahil). Konsolda eklentiye ait tek bir hata çıkarsa sürüm yayınlanmaz.

---

## İçindekiler
1. [Kurulum](#kurulum)
2. [Kurulumdan önce config'de değiştirmeniz gerekenler](#kurulumdan-önce-configde-değiştirmeniz-gerekenler)
3. [Komutlar](#komutlar)
4. [Yetkiler](#yetkiler)
5. [Alt hesap & ban sistemi](#alt-hesap--ban-sistemi)
6. [Özellikler](#özellikler)
7. [v1.x'ten yükseltme](#v1xten-yükseltme)
8. [Sorun giderme](#sorun-giderme)
9. [Derleme](#derleme)
10. [English](#english)

---

## Kurulum
1. [Releases](https://github.com/jesthub25/Kay-t-Sistem/releases) sayfasından `KayitPlugin-<sürüm>.jar` dosyasını indirin.
2. `plugins/` klasörüne atın, sunucuyu başlatın (veya `/plugman load KayitPlugin`).
3. `plugins/KayitPlugin/config.yml` oluşur → aşağıdaki ayarları yapın → `/kayitsistemi yenile`.
4. Test edin: `/kayitsistemi testmail sizin@mailiniz.com`

> Eski `KayitPlugin-1.x.jar` dosyasını silmeyi unutmayın. Veritabanı otomatik taşınır.

## Kurulumdan önce config'de değiştirmeniz gerekenler

**Zorunlu (e-posta doğrulaması için):**

```yaml
email:
  username: "sunucunuz@gmail.com"      # Gmail adresiniz
  password: "abcd efgh ijkl mnop"      # Gmail UYGULAMA ŞİFRESİ (normal şifre çalışmaz)
  from-name: "AvenorCraft"
```

**Gmail uygulama şifresi nasıl alınır?**
1. Google Hesabı → **Güvenlik** → **2 Adımlı Doğrulama**'yı açın.
2. Google Hesabı → arama kutusuna **"Uygulama şifreleri"** yazın.
3. Uygulama adı: `AvenorCraft` → **Oluştur** → çıkan 16 haneli şifreyi `password` alanına yazın (boşluklu yazabilirsiniz).

Hosting 587 portunu engelliyorsa:
```yaml
  smtp-port: 465
  security: SSL
```

**İsteğe bağlı (en çok değiştirilenler):**

| Ayar | Varsayılan | Açıklama |
|---|---|---|
| `language` | `tr` | `tr` / `en` — mesajlar **ve komut adları** bu dilde olur |
| `password.min-length` / `max-length` | `5` / `16` | Şifre uzunluk sınırı |
| `ip-limits.max-registrations-per-ip` | `3` | Aynı IP'den en fazla kayıt (0 = sınırsız) |
| `ip-limits.max-sessions-per-ip` | `2` | Aynı IP'den aynı anda en fazla giriş (0 = sınırsız) |
| `email.require-code-on-login` | `true` | E-postası doğrulanmış oyuncular her girişte kod girer |
| `ban-sources.refresh-seconds` | `60` | Ban listesinin yenilenme süresi |
| `database.type` | `sqlite` | `sqlite` / `mysql` / `mariadb` |
| `security.login-timeout-seconds` | `90` | Bu sürede giriş yapmayan atılır |
| `session.enabled` | `false` | Aynı IP'den kısa sürede dönen oyuncu şifresiz girer |
| `webhook.*` | kapalı | Discord'a kayıt / banlı alt / yönetici işlemi logu |

Tüm ayarlar `config.yml` içinde Türkçe açıklamalıdır. Mesajları ve e-posta şablonlarını `lang/tr.yml` / `lang/en.yml` dosyalarından düzenleyebilirsiniz (HEX renk `&#RRGGBB` desteklenir).

## Komutlar

Komut adları seçilen dile göre değişir (`lang/<dil>.yml → commands`). Adları ve takma adları oradan özgürce değiştirebilirsiniz.

| İşlev | Türkçe (`tr`) | English (`en`) | Yetki |
|---|---|---|---|
| Kayıt ol | `/kayitol <şifre> [e-posta]` | `/register <password> [email]` | — |
| Giriş yap | `/giris <şifre>` | `/login <password>` | — |
| E-posta kodu | `/kod <kod>` · `/kod gonder` | `/code <code>` · `/code resend` | — |
| E-posta yönetimi | `/eposta bagla <e-posta>` · `kaldir` · `degistir <mevcut> <yeni>` | `/email add` · `remove` · `change` | — |
| Şifre değiştir | `/sifredegistir <eski> <yeni>` | `/changepassword <old> <new>` | — |
| Alt hesaplar | `/hesaplar <oyuncu>` | `/alts <player>` | `kayit.alts` |
| Şifresiz giriş | `/engelsizgiris <oyuncu>` | `/forcelogin <player>` | `kayit.forcelogin` |
| Şifre sıfırla | `/sifresifirla <oyuncu>` | `/resetpassword <player>` | `kayit.resetpassword` |
| E-posta sıfırla | `/epostasifirla <oyuncu>` | `/resetemail <player>` | `kayit.resetemail` |
| Yönetim | `/kayitsistemi yenile \| bilgi \| testmail <e-posta>` | `/authsystem reload \| info \| testmail <email>` | `kayit.manage` |

Yetkisi olmayan oyuncular yönetici komutlarını **göremez** (tab listesinde de çıkmaz).
Türkçe modda oyuncu alışkanlığı için `/register` ve `/login` takma adları da açıktır.

## Yetkiler

| Yetki | Varsayılan | Açıklama |
|---|---|---|
| `kayit.admin` | op | Aşağıdaki tüm yönetici yetkileri |
| `kayit.alts` | op | `/hesaplar` |
| `kayit.alert` | op | Banlı alt hesap uyarılarını görür |
| `kayit.forcelogin` | op | `/engelsizgiris` |
| `kayit.resetpassword` | op | `/sifresifirla` |
| `kayit.resetemail` | op | `/epostasifirla` |
| `kayit.manage` | op | `/kayitsistemi` |
| `kayit.bypass.iplimit` | kimse | IP kayıt/oturum limitlerini yok sayar |

## Alt hesap & ban sistemi

### `/hesaplar <oyuncu>`
Oyuncunun kullandığı **tüm IP'leri** (veya `scan-all-ips: false` ise sadece son IP'yi) tarar, bu IP'lerden kayıt olan / giriş yapan
bütün hesapları listeler. Çıktı **sadece komutu yazan yetkiliye** gönderilir:

```
-------- YemenKedisi İçin Alt Hesaplar --------
Toplam 3 hesap • 1 banlı • 2 IP tarandı
İsim  |  Aktiflik  |  Ban Durumu (sebep, tarih)  |  Banı Atan
- YemenKedisi (sorgulanan)  |  Şu an aktif  |  Temiz  |  -
- memetalierbil12  |  2 gün önce aktifti  |  Hile sebebiyle ban yedi 12/07/2026 18.23 (kalıcı)  |  Jestron
- eskihesap  |  3 ay önce aktifti  |  Önceden banlandı: Küfür (01/03/2026 12.00)  |  Moderator
```

- Banlı olmasa bile **tüm alt hesaplar** gösterilir.
- Satırın üzerine gelince UUID, kayıt tarihi, son giriş, ortak IP sayısı ve ban kaynağı görünür; tıklayınca o hesabı sorgular.
- Format tamamen `lang/tr.yml → alts` bölümünden değiştirilebilir.

### Banlı alt hesap uyarısı
Bir oyuncu sunucuya girdiğinde veya kayıt olduğunda, alt hesaplarından biri **şu an banlıysa** `kayit.alert` yetkisine sahip
yetkililere (sadece onlara) tıklanabilir uyarı gider:

```
[Kayıt] ⚠ YemenKedisi adlı oyuncu banlı bir alt hesap bulunduruyor! (memetalierbil12)
```

### Okunan ban kaynakları
| Kaynak | Nasıl okunur |
|---|---|
| Vanilla `/ban` | Bukkit ban listesi |
| EssentialsX | Bukkit ban listesi |
| LiteBans | Resmi API (H2/SQLite/MySQL hepsi) · proxy'deyse `ban-sources.litebans.sql` |
| AdvancedBan | Resmi API (HSQLDB/MySQL) · proxy'deyse `ban-sources.advancedban.sql` |
| Diğerleri (CMI, BanManager…) | **Komut izleyici**: `/ban`, `/tempban`, `/unban` … komutları kaydedilir |

- Ban listesi her **60 saniyede** yeniden okunur: ban attığınızda en geç 1 dakika içinde "banlı", banı açtığınızda en geç 1 dakika içinde "temiz" görünür.
- Ayrıca her ban/unban komutundan **~3 saniye sonra** liste anında yenilenir.
- Komut izleyici oyuncu, konsol ve RCON komutlarını izler; yetkisi yetmeyen komutları kaydetmez.

## Özellikler
- BCrypt şifreleme (maliyet ayarlanabilir), tüm şifre/veritabanı/e-posta işlemleri arka planda — **sunucu asla donmaz**.
- E-posta ile kayıt, giriş (2 adımlı), e-posta bağlama/kaldırma/değiştirme doğrulaması; HTML veya düz metin e-postalar.
- Kod süresi, yeniden gönderme bekleme süresi, yanlış kod hakkı, e-posta başına hesap limiti.
- Şifre kuralları: min/max uzunluk, kullanıcı adıyla aynı olamaz, yasaklı kolay şifreler.
- IP kayıt limiti ve eş zamanlı oturum limiti.
- Giriş süresi dolunca atma, çok yanlış şifrede atma, hesap oyundayken aynı isimle girip onu atmayı engelleme.
- Giriş yapmamış oyuncu: hareket (düşme serbest, "flying" kick'i yok), sohbet, komut, blok, envanter, eşya, etkileşim, hasar, mob hedefleme, açlık korumaları; isteğe bağlı körlük ve ekran başlığı.
- İsteğe bağlı oturum hatırlama (aynı IP), `/plugman reload` sonrası giriş yapmış oyuncular korunur.
- Discord webhook logları.
- NPC'ler (Citizens vb.) etkilenmez.

## v1.x'ten yükseltme
- Eski `players.db` ve tabloları **otomatik taşınır** (IP geçmişi, son giriş). Şifreler aynen çalışır.
- Eski `config.yml` silinmeden okunur; yeni ayarlar varsayılan değerleriyle çalışır. Tüm yeni seçenekleri görmek için eski dosyayı yedekleyip silebilirsiniz.
- Mesajlar artık `config.yml → messages` yerine `lang/tr.yml` dosyasındadır.
- Eski sürümdeki **"e-postaya kod gitmiyor"** hatası giderildi (relocate edilmiş JavaMail'in sağlayıcı dosyaları bozuktu; artık SMTP doğrudan bağlanıyor).
- Eski sürümdeki **`UnsatisfiedLinkError` (sqlite)** hatası giderildi (SQLite/MySQL sürücüleri artık JAR'a gömülmüyor, sunucununki kullanılıyor).

## Sorun giderme
| Belirti | Çözüm |
|---|---|
| Konsolda `SMTP bağlantı testi BAŞARISIZ: Kimlik doğrulama başarısız` | Gmail **uygulama şifresi** kullanın, 2 adımlı doğrulama açık olmalı |
| `SMTP sunucusuna bağlanılamadı` | Hosting portu engelliyor: `smtp-port: 465` + `security: SSL` deneyin |
| E-posta gidiyor ama gelmiyor | Spam klasörüne bakın; `from-address` gönderen hesapla aynı olmalı |
| `'/giris' komutu başka bir eklenti tarafından kullanılıyor` | Diğer giriş eklentisini (AuthMe vb.) kaldırın veya `lang/tr.yml → commands` altından adı değiştirin |
| Durum kontrolü | `/kayitsistemi bilgi` — e-posta, veritabanı ve tüm ban kaynaklarının durumunu gösterir |
| Test e-postası | `/kayitsistemi testmail adres@mail.com` — hatanın tam açıklamasını gösterir |

## Derleme
```bash
mvn package
# target/KayitPlugin-<sürüm>.jar
```
JDK 17+ ile derlenir, **Java 8 bytecode** üretir (1.16 sunucularında da çalışması için).
Uçtan uca test: `bash scripts/smoke_test.sh 1.21.4 target/KayitPlugin-*.jar` (Node.js 18+ gerekir).

---

# English

**KayitPlugin** is a lightweight login/register plugin with email verification, IP limits, **alt account detection** and **ban tracking**.
One JAR for **Spigot / Paper / Purpur / Leaf 1.16.x – 1.21.x** (Java 8 – 21), SQLite / MySQL / MariaDB.

### Quick setup
1. Drop the JAR from [Releases](https://github.com/jesthub25/Kay-t-Sistem/releases) into `plugins/` and start the server.
2. In `plugins/KayitPlugin/config.yml` set `language: en`, then fill in `email.username` and `email.password`
   (for Gmail: enable 2-Step Verification and create an **App Password**; port 587 = `STARTTLS`, port 465 = `SSL`).
3. Run `/authsystem reload`, then `/authsystem testmail you@example.com`.

### Commands (language `en`)
`/register <password> [email]`, `/login <password>`, `/code <code>` / `/code resend`, `/email add|remove|change`,
`/changepassword <old> <new>`; staff: `/alts <player>`, `/forcelogin`, `/resetpassword`, `/resetemail`,
`/authsystem reload|info|testmail`. Command names and aliases are editable in `lang/en.yml → commands`.
Players without permission cannot see staff commands.

### Alt accounts & bans
`/alts <player>` lists every account that used the player's IP addresses, with activity ("Active 2 days ago"),
ban status (reason, date, expiry) and the staff member who issued the ban — shown only to the command sender.
When a player joins or registers and one of their alts is currently banned, staff with `kayit.alert` receive a clickable alert.
Bans are read from the Bukkit ban list (vanilla, EssentialsX), LiteBans and AdvancedBan (official APIs, or MySQL for proxy setups),
and a command monitor covers any other ban plugin. The ban cache refreshes every 60 seconds and ~3 seconds after any ban/unban command.

### Permissions
`kayit.admin` (all staff permissions), `kayit.alts`, `kayit.alert`, `kayit.forcelogin`, `kayit.resetpassword`,
`kayit.resetemail`, `kayit.manage`, `kayit.bypass.iplimit`.

### Build
`mvn package` → `target/KayitPlugin-<version>.jar` (built with JDK 17+, Java 8 bytecode).
