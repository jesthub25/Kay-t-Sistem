'use strict';
/*
 * KayitPlugin uctan uca test.
 * Gercek bir Paper sunucusu baslatir, mineflayer botlariyla kayit/giris/kod/alt hesap/ban senaryolarini oynatir,
 * konsol ciktisinda tek bir hata/exception bile varsa testi basarisiz sayar.
 *
 * Kullanim: node e2e.js <sunucu-klasoru> <mc-surumu>
 */
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');

const SERVER_DIR = path.resolve(process.argv[2] || 'server');
const MC = process.argv[3] || '1.21.4';
const JAVA_OPTS = ['-Xms512M', '-Xmx1536M', '-Dcom.mojang.eula.agree=true'];

const lines = [];
let waiters = [];
const logFile = fs.createWriteStream(path.join(SERVER_DIR, 'e2e-console.log'));
const passed = [];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const stripAnsi = (s) => s.replace(/\x1b\[[0-9;]*[A-Za-z]/g, '').replace(/§[0-9a-fk-orx]/gi, '');

function onLine(raw) {
  const line = stripAnsi(raw);
  lines.push(line);
  logFile.write(line + '\n');
  process.stdout.write('[server] ' + line + '\n');
  for (const w of waiters.slice()) {
    if (w.re.test(line)) {
      waiters = waiters.filter((x) => x !== w);
      clearTimeout(w.timer);
      w.resolve(line);
    }
  }
}

function waitLog(re, ms, from) {
  const start = from || 0;
  for (let i = start; i < lines.length; i++) {
    if (re.test(lines[i])) return Promise.resolve(lines[i]);
  }
  return new Promise((resolve, reject) => {
    const w = { re, resolve };
    w.timer = setTimeout(() => {
      waiters = waiters.filter((x) => x !== w);
      reject(new Error('Server log timeout: ' + re));
    }, ms);
    waiters.push(w);
  });
}

let proc;
function startServer() {
  proc = spawn('java', JAVA_OPTS.concat(['-jar', 'paper.jar', '--nogui']), { cwd: SERVER_DIR });
  let buf = '';
  const feed = (chunk) => {
    buf += chunk.toString('utf8');
    let idx;
    while ((idx = buf.indexOf('\n')) >= 0) {
      onLine(buf.slice(0, idx).replace(/\r$/, ''));
      buf = buf.slice(idx + 1);
    }
  };
  proc.stdout.on('data', feed);
  proc.stderr.on('data', feed);
}

function consoleCmd(cmd) {
  const mark = lines.length;
  proc.stdin.write(cmd + '\n');
  return mark;
}

function step(name) {
  passed.push(name);
  console.log('::notice title=MC ' + MC + '::OK - ' + name);
}

// ------------------------------------------------------------------ bot

function createBot(name) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: '127.0.0.1',
      port: 25565,
      username: name,
      version: MC,
      auth: 'offline',
      checkTimeoutInterval: 120000,
    });
    bot.msgs = [];
    bot.waiters = [];
    bot.on('messagestr', (m) => {
      const s = String(m);
      bot.msgs.push(s);
      console.log('[' + name + '] ' + s);
      for (const w of bot.waiters.slice()) {
        if (w.re.test(s)) {
          bot.waiters = bot.waiters.filter((x) => x !== w);
          clearTimeout(w.timer);
          w.resolve(s);
        }
      }
    });
    bot.on('kicked', (reason) => {
      bot.kickReason = typeof reason === 'string' ? reason : JSON.stringify(reason);
      console.log('[' + name + '] kicked: ' + bot.kickReason);
    });
    bot.once('spawn', () => resolve(bot));
    bot.once('error', (e) => reject(e));
    bot.once('end', (r) => {
      bot.ended = true;
      console.log('[' + name + '] disconnected: ' + r);
    });
    bot.expect = (re, ms, from) => {
      const start = from || 0;
      for (let i = start; i < bot.msgs.length; i++) {
        if (re.test(bot.msgs[i])) return Promise.resolve(bot.msgs[i]);
      }
      return new Promise((res, rej) => {
        const w = { re, resolve: res };
        w.timer = setTimeout(() => {
          bot.waiters = bot.waiters.filter((x) => x !== w);
          rej(new Error(name + ' did not receive: ' + re));
        }, ms || 15000);
        bot.waiters.push(w);
      });
    };
    bot.cmd = (text) => {
      const mark = bot.msgs.length;
      bot.chat(text);
      return mark;
    };
  });
}

async function quit(bot) {
  try {
    bot.quit();
  } catch (e) {
    // yok say
  }
  await sleep(1500);
}

function setLanguage(code) {
  const cfg = path.join(SERVER_DIR, 'plugins', 'KayitPlugin', 'config.yml');
  const text = fs.readFileSync(cfg, 'utf8').replace(/^language:.*$/m, 'language: ' + code);
  fs.writeFileSync(cfg, text, 'utf8');
}

// ------------------------------------------------------------------ senaryo

async function scenario() {
  await waitLog(/Done \(/, 360000);
  await waitLog(/KayitPlugin v2\.\d+\.\d+ etkin/, 5000);
  step('Plugin enabled on server start');
  await sleep(3000);

  // 1) Alt hesap kaydi
  let alt = await createBot('AltBot');
  await alt.expect(/\/kayitol/, 15000);
  let m = alt.cmd('/kayitol altpass1');
  await alt.expect(/Kayıt başarılı/, 15000, m);
  step('Register (AltBot)');
  await quit(alt);

  // 2) Ana hesap: kisa sifre, giris oncesi sohbet, kayit, yetkisiz admin komutu
  let bot = await createBot('TestBot');
  await bot.expect(/\/kayitol/, 15000);
  m = bot.cmd('selam');
  await bot.expect(/Sohbet için önce giriş/, 10000, m);
  step('Chat blocked before login');
  m = bot.cmd('/spawn');
  await bot.expect(/önce giriş yapmalısın|Unknown|Bilinmeyen|incomplete/i, 10000, m);
  step('Commands blocked before login');
  m = bot.cmd('/kayitol abc');
  await bot.expect(/çok kısa/, 10000, m);
  step('Password min-length enforced');
  m = bot.cmd('/kayitol 12345678901234567890');
  await bot.expect(/çok uzun/, 10000, m);
  step('Password max-length enforced');
  m = bot.cmd('/kayitol testpass1');
  await bot.expect(/Kayıt başarılı/, 15000, m);
  step('Register (TestBot)');
  m = bot.cmd('/hesaplar TestBot');
  await bot.expect(/yetkin yok|Unknown|Bilinmeyen|incomplete/i, 10000, m);
  step('/hesaplar hidden for players without permission');
  await quit(bot);

  // 3) Giris: yanlis sifre, dogru sifre, sohbet
  bot = await createBot('TestBot');
  await bot.expect(/\/giris/, 15000);
  m = bot.cmd('/giris yanlis99');
  await bot.expect(/Yanlış şifre/, 15000, m);
  step('Wrong password rejected');
  m = bot.cmd('/giris testpass1');
  await bot.expect(/Giriş başarılı/, 15000, m);
  step('Login');
  let mark = lines.length;
  bot.chat('merhaba dunya');
  await waitLog(/merhaba dunya/, 10000, mark);
  step('Chat works after login');
  m = bot.cmd('/kod 123456');
  await bot.expect(/Bekleyen bir doğrulama/, 10000, m);
  step('Code command without pending action');
  await quit(bot);

  // 4) Ban -> alt hesap uyarisi
  mark = consoleCmd('ban AltBot hile testi');
  await sleep(6000);
  mark = lines.length;
  bot = await createBot('TestBot');
  await waitLog(/Banlı alt hesap tespiti: TestBot -> AltBot/, 20000, mark);
  step('Banned-alt staff alert on join');
  m = bot.cmd('/giris testpass1');
  await bot.expect(/Giriş başarılı/, 15000, m);

  mark = consoleCmd('hesaplar TestBot');
  await waitLog(/AltBot.*hile testi sebebiyle ban yedi/, 15000, mark);
  await waitLog(/TestBot.*sorgulanan.*Temiz/, 5000, mark);
  step('/hesaplar shows banned alt with reason, date and staff');

  // 5) Unban -> 1 dakika icinde temiz
  consoleCmd('pardon AltBot');
  await sleep(7000);
  mark = consoleCmd('hesaplar TestBot');
  await waitLog(/AltBot.*Temiz/, 15000, mark);
  step('Unban reflected in /hesaplar');

  // 6) Yonetici komutlari
  mark = consoleCmd('kayitsistemi bilgi');
  await waitLog(/Kayıt Sistemi/, 10000, mark);
  await waitLog(/Veritabanı: SQLite/, 5000, mark);
  step('Admin info');
  mark = consoleCmd('sifresifirla AltBot');
  await waitLog(/şifresi sıfırlandı/, 10000, mark);
  step('Admin password reset');
  await quit(bot);

  // 7) Dil degisimi: komutlar Ingilizceye gecer
  setLanguage('en');
  mark = consoleCmd('kayitsistemi yenile');
  await waitLog(/reloaded|yeniden yüklendi/, 15000, mark);
  await sleep(2000);
  const en = await createBot('EnBot');
  await en.expect(/\/register/, 15000);
  m = en.cmd('/register enpass12');
  await en.expect(/Registration successful/, 15000, m);
  step('Language switch to English (commands + messages)');
  mark = consoleCmd('authsystem info');
  await waitLog(/Auth System/, 10000, mark);
  step('English admin command');
  await quit(en);
}

function scanErrors() {
  const bad = lines.filter((l) =>
    /Error occurred while enabling KayitPlugin/.test(l) ||
    /Could not pass event .* to KayitPlugin/.test(l) ||
    /at com\.jestron\./.test(l) ||
    /com\.jestron\.[\w.$]+(Exception|Error)/.test(l) ||
    (/KayitPlugin/.test(l) && /\b(ERROR|SEVERE)\b/.test(l)) ||
    /Task .* generated an exception/.test(l) && /KayitPlugin/.test(l));
  return bad;
}

async function main() {
  startServer();
  let failure = null;
  try {
    await scenario();
  } catch (e) {
    failure = e;
  }
  try {
    consoleCmd('stop');
  } catch (e) {
    // yok say
  }
  const exited = await Promise.race([
    new Promise((r) => proc.once('exit', () => r(true))),
    sleep(90000).then(() => false),
  ]);
  if (!exited) proc.kill('SIGKILL');
  await sleep(500);

  const bad = scanErrors();
  for (const b of bad.slice(0, 30)) console.log('::error title=MC ' + MC + ' console::' + b);
  if (failure) console.log('::error title=MC ' + MC + '::' + failure.message);
  console.log('\nPassed steps (' + passed.length + '): ' + passed.join(' | '));
  if (failure || bad.length) {
    process.exit(1);
  }
  console.log('::notice title=MC ' + MC + '::ALL ' + passed.length + ' CHECKS PASSED, 0 errors in console');
  process.exit(0);
}

main().catch((e) => {
  console.log('::error::' + (e && e.stack ? e.stack : e));
  process.exit(1);
});
