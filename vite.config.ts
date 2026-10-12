import { readFileSync } from 'node:fs';
import { defineConfig } from 'vite';

// Uygulama sürümü tek yerden: package.json (Android versionCode da buradan hesaplanır).
const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'));

export default defineConfig({
  define: { __SURUM__: JSON.stringify(version) },
});
