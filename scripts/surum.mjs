// Yeni sürüm yayınlar:  npm run surum -- 1.0.1 "Yenilik 1" "Yenilik 2"
//  1. package.json sürümünü yükseltir (Android versionCode da buradan hesaplanır)
//  2. Testleri çalıştırır, APK'yı derler (scripts/apk.mjs)
//  3. APK'yı indir/ klasörüne koyar, surum.json'u yazar
//  4. Commit + etiket (vX.Y.Z) + push: telefondaki uygulama yeni sürümü görür
// APK hep aynı anahtarla (~/.android/debug.keystore) imzalanır; yoksa güncelleme kurulamaz.
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const kok = join(dirname(fileURLToPath(import.meta.url)), '..');
const [yeni, ...notlar] = process.argv.slice(2);

function dur(mesaj) {
  console.error(`\n✖ ${mesaj}`);
  process.exit(1);
}
function calistir(komut) {
  console.log(`\n> ${komut}`);
  const s = spawnSync(komut, { cwd: kok, shell: true, stdio: 'inherit' });
  if (s.status !== 0) dur(`Komut başarısız: ${komut}`);
}
const surumSayi = (s) => s.split('.').map(Number).reduce((t, n) => t * 100 + n, 0);

const pkgYolu = join(kok, 'package.json');
const pkg = JSON.parse(readFileSync(pkgYolu, 'utf8'));
if (!/^\d+\.\d+\.\d+$/.test(yeni ?? '')) dur('Kullanım: npm run surum -- 1.0.1 "Yenilik 1" "Yenilik 2"');
if (yeni.split('.').some((n) => Number(n) > 99)) dur('Sürüm parçaları 0–99 arasında olmalı (Android versionCode için).');
if (surumSayi(yeni) <= surumSayi(pkg.version)) dur(`Yeni sürüm (${yeni}) mevcut sürümden (${pkg.version}) büyük olmalı.`);

const kirli = spawnSync('git status --porcelain --untracked-files=no', { cwd: kok, shell: true, encoding: 'utf8' }).stdout.trim();
if (kirli) dur(`Önce değişikliklerini commit et:\n${kirli}`);

const imza = join(homedir(), '.android', 'debug.keystore');
if (!existsSync(imza)) dur(`İmza anahtarı yok: ${imza}\nFarklı anahtarla imzalanan APK eski sürümün üzerine kurulamaz.`);

// 1. Sürüm
pkg.version = yeni;
writeFileSync(pkgYolu, JSON.stringify(pkg, null, 2) + '\n');
const kilitYolu = join(kok, 'package-lock.json');
if (existsSync(kilitYolu)) {
  const kilit = JSON.parse(readFileSync(kilitYolu, 'utf8'));
  kilit.version = yeni;
  if (kilit.packages?.['']) kilit.packages[''].version = yeni;
  writeFileSync(kilitYolu, JSON.stringify(kilit, null, 2) + '\n');
}

// 2. Test ve APK
calistir('npm test');
calistir('node scripts/apk.mjs');

// 3. APK ve sürüm bilgisi
mkdirSync(join(kok, 'indir'), { recursive: true });
copyFileSync(join(kok, 'android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'), join(kok, 'indir', 'Dedektor.apk'));
writeFileSync(join(kok, 'surum.json'), JSON.stringify({ surum: yeni, notlar }, null, 2) + '\n');

// 4. Yayınla
// Başlıktan sonra boş satır: yoksa git tüm notları başlık sayar.
const mesaj = [`Sürüm ${yeni}`, '', ...notlar.map((n) => `- ${n}`)].join('\n');
calistir('git add package.json package-lock.json indir surum.json');
const commit = spawnSync('git', ['commit', '-q', '-m', mesaj], { cwd: kok, stdio: 'inherit' });
if (commit.status !== 0) dur('Commit başarısız.');
calistir(`git tag v${yeni}`);
calistir(`git push --atomic origin main v${yeni}`);
console.log(`\n✔ Sürüm ${yeni} yayınlandı. Telefondaki uygulama açılışta güncellemeyi gösterir.`);
