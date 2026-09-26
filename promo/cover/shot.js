const p = require('puppeteer-core');
(async () => {
  const b = await p.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: 'new', userDataDir: 'C:/Users/rapha/AppData/Local/Temp/claude/edge-cover-' + process.pid, args: ['--disable-gpu'] });
  const pg = await b.newPage(); await pg.setViewport({ width: 2400, height: 1600, deviceScaleFactor: 1 });
  await pg.goto('http://localhost:8771/cover.html', { waitUntil: 'load' });
  await pg.evaluate(() => document.fonts.ready); await new Promise(r => setTimeout(r, 800));
  await pg.screenshot({ path: 'CampusMaps-cover-3x2.png', clip: { x: 0, y: 0, width: 2400, height: 1600 } });
  await b.close();
})();
