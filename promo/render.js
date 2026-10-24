// Frame renderer for the QVK promo.
//   node render.js stills <outDir> <t1> <t2> ...          → PNG stills for review
//   node render.js video <out.mkv> <fps> <from> <to>      → frames [from, to) piped into a near-lossless x264 segment
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

async function open() {
  const browser = await chromium.launch({ args: ['--force-color-profile=srgb', '--disable-lcd-text', '--font-render-hinting=none'] });
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => { console.error('PAGE ERROR', e); process.exit(1); });
  await page.goto('file://' + path.join(__dirname, 'index.html'));
  await page.evaluate(() => window.ready);
  return { browser, page };
}

(async () => {
  const [mode, out, ...rest] = process.argv.slice(2);
  const { browser, page } = await open();
  if (mode === 'stills') {
    fs.mkdirSync(out, { recursive: true });
    for (const ts of rest) {
      await page.evaluate((t) => window.seek(t), parseFloat(ts));
      await page.screenshot({ path: path.join(out, `t${ts}.png`) });
    }
  } else {
    const fps = +rest[0], from = +rest[1], to = +rest[2];
    const ff = spawn('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'png', '-i', '-',
      '-c:v', 'libx264', '-preset', 'fast', '-crf', '6', '-pix_fmt', 'yuv444p', out], { stdio: ['pipe', 'inherit', 'inherit'] });
    const t0 = Date.now();
    for (let f = from; f < to; f++) {
      await page.evaluate((t) => window.seek(t), f / fps);
      const buf = await page.screenshot({ type: 'png' });
      if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
      if ((f - from) % 120 === 0) console.log(`${out}: frame ${f} (${((Date.now() - t0) / 1000).toFixed(0)}s)`);
    }
    ff.stdin.end();
    await new Promise((r) => ff.on('close', r));
  }
  await browser.close();
})();
