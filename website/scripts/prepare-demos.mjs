import { cpSync, mkdirSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const frontend = `${root}projects/family-reading-tracker/frontend`;
execFileSync(npm, ['ci'], { cwd: frontend, stdio: 'inherit', shell: process.platform === 'win32' });
execFileSync(npm, ['run', 'build:demo'], { cwd: frontend, stdio: 'inherit', shell: process.platform === 'win32' });
const destination = `${root}website/public/demos`;
rmSync(destination, { recursive: true, force: true });
mkdirSync(`${destination}/kalshi`, { recursive: true });
cpSync(`${frontend}/dist`, `${destination}/reading-tracker`, { recursive: true });
for (const file of ['index.html', 'style.css', 'app.mjs', 'quality.mjs']) {
  cpSync(`${root}projects/kalshi-brti-monitor/demo/${file}`, `${destination}/kalshi/${file}`);
}
console.log('Prepared both sample-data demos.');
