const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const root = path.resolve(__dirname, '..', '..');
const svgPath = path.join(root, 'tools', 'icon', 'icon.svg');
const svg = fs.readFileSync(svgPath, 'utf8');

const targets = [
  ['mipmap-mdpi', 48],
  ['mipmap-hdpi', 72],
  ['mipmap-xhdpi', 96],
  ['mipmap-xxhdpi', 144],
  ['mipmap-xxxhdpi', 192],
];

for (const [dir, size] of targets) {
  const resvg = new Resvg(svg, {
    fitTo: { mode: 'width', value: size },
    background: 'transparent',
  });
  const png = resvg.render().asPng();
  const outDir = path.join(root, 'app', 'src', 'main', 'res', dir);
  fs.mkdirSync(outDir, { recursive: true });
  for (const name of ['ic_launcher.png', 'ic_launcher_round.png']) {
    fs.writeFileSync(path.join(outDir, name), png);
  }
  console.log(`${dir} ${size}x${size} ok (${png.length} B)`);
}

const store = new Resvg(svg, { fitTo: { mode: 'width', value: 512 } }).render().asPng();
fs.writeFileSync(path.join(root, 'tools', 'icon', 'ic_launcher_store_512.png'), store);
console.log('store 512 ok');
