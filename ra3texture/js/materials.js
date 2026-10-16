(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');

  // Бібліотека матеріалів RA3 для пензля, шарів-заливок і «розумних матеріалів».
  // Material = { color: [r,g,b] (гамма, як у Назва.dds), alpha: 0..255, height: 0..1 (0.5 = рівно),
  //              spec: 0..255 (R у _SPM: блиск і відбиття), team: 0..255 (B у _SPM: маска кольору гравця) }.
  // Гра фарбує колір гравця як lerp(c, c·T·2, team), тож під team 255 колір має бути ~50% сірим (128).
  // Кольори фракцій збігаються з пресетами generators.js (основа фракції), щоб шари й заливки пасували одне до одного.

  const CHANNELS = ['color', 'alpha', 'height', 'spec', 'team'];

  // 'color spec team' → { color: true, alpha: false, height: false, spec: true, team: true }
  function channels(list) {
    const on = list.split(' '), out = {};
    for (const c of CHANNELS) out[c] = on.includes(c);
    return out;
  }

  function mat(color, spec, extra) {
    return Object.assign({ color, alpha: 255, height: 0.5, spec, team: 0 }, extra || {});
  }

  const PAINT = 'color spec team'; // фарба перекриває колір гравця під собою (team = 0)

  const list = [
    { id: 'allied-light', label: 'Альянс: світла фарба', material: mat([159, 169, 176], 45), channels: channels(PAINT) },
    { id: 'allied-dark', label: 'Альянс: темна фарба', material: mat([74, 85, 96], 35), channels: channels(PAINT) },
    { id: 'soviet-red', label: 'СРСР: червона фарба', material: mat([122, 46, 36], 40), channels: channels(PAINT) },
    { id: 'soviet-steel', label: 'СРСР: темна сталь', material: mat([75, 74, 69], 90), channels: channels(PAINT) },
    { id: 'empire-white', label: 'Імперія: біла броня', material: mat([216, 212, 200], 70), channels: channels(PAINT) },
    { id: 'empire-graphite', label: 'Імперія: графіт', material: mat([47, 50, 54], 60), channels: channels(PAINT) },
    { id: 'bare-steel', label: 'Гола сталь', material: mat([200, 200, 192], 210), channels: channels(PAINT) },
    { id: 'rust', label: 'Іржа', material: mat([138, 74, 34], 0), channels: channels(PAINT) },
    { id: 'dirt', label: 'Бруд', material: mat([91, 76, 58], 8), channels: channels(PAINT) },
    { id: 'dust', label: 'Пил / пісок', material: mat([168, 150, 118], 5), channels: channels(PAINT) },
    { id: 'soot', label: 'Кіптява', material: mat([32, 30, 28], 4), channels: channels(PAINT) },
    { id: 'rubber', label: 'Гума / гусениці', material: mat([38, 38, 36], 15), channels: channels(PAINT) },
    { id: 'glass', label: 'Скло кабіни', material: mat([28, 44, 66], 230), channels: channels(PAINT) },
    { id: 'concrete', label: 'Бетон', material: mat([176, 174, 166], 10), channels: channels(PAINT) },
    { id: 'team-paint', label: 'Фарба гравця', material: mat([128, 128, 128], 60, { team: 255 }), channels: channels('color spec team') },
    { id: 'seam', label: 'Шов / заглиблення', material: mat([128, 128, 128], 0, { height: 0.3 }), channels: channels('height') },
    { id: 'bump', label: 'Опуклість', material: mat([128, 128, 128], 0, { height: 0.7 }), channels: channels('height') },
    { id: 'gloss-up', label: 'Блиск +', material: mat([128, 128, 128], 200), channels: channels('spec') },
    { id: 'matte', label: 'Матовий', material: mat([128, 128, 128], 0), channels: channels('spec') },
    { id: 'cutout', label: 'Прозорість', material: mat([128, 128, 128], 0, { alpha: 0 }), channels: channels('alpha') }
  ];
  const byId = {};
  for (const m of list) byId[m.id] = m;

  const copyMaterial = m => ({ color: m.color.slice(), alpha: m.alpha, height: m.height, spec: m.spec, team: m.team });

  function find(id) {
    const m = Object.prototype.hasOwnProperty.call(byId, id) ? byId[id] : null;
    if (!m) throw new Error('Невідомий матеріал: ' + id);
    return m;
  }

  // Глибока копія запису списку (щоб шар чи пензель міг її змінювати).
  function get(id) {
    const m = find(id);
    return { id: m.id, label: m.label, material: copyMaterial(m.material), channels: Object.assign({}, m.channels) };
  }

  // ---------- Розумні матеріали ----------

  const smart = [
    { id: 'allied-worn', label: 'Альянс: фарба зі зношеними краями',
      description: 'Світла фарба Альянсу з темнішими плямами, гола сталь на зношених краях і бруд у швах.' },
    { id: 'soviet-rusty', label: 'СРСР: червона сталь з іржею',
      description: 'Червона фарба, темна сталь на краях, іржа у швах і плямами, бруд знизу.' },
    { id: 'empire-clean', label: 'Імперія: біла броня',
      description: 'Чиста біла броня з графітом у швах і легкою варіацією блиску.' },
    { id: 'bare-metal', label: 'Гола сталь з подряпинами',
      description: 'Блискуча сталь з тьмяними плямами, подряпинами, яскравими краями й брудом у швах.' },
    { id: 'concrete-building', label: 'Бетон будівлі',
      description: 'Матовий бетон з плямами, брудом у швах, патьоками й брудом знизу. Для будівель.' },
    { id: 'team-stripes-worn', label: 'Колір гравця зі зношенням',
      description: 'Зона кольору гравця перефарбовується в сірий 50% (правильне фарбування в грі), краї стираються до сталі.' }
  ];

  // Рецепти: [назва, матеріал, канали, непрозорість, маска-генератор | null, зміни матеріалу | null]; знизу вгору.
  // Основна фарба не вмикає канал team, щоб не стерти наявну маску гравця під розумним матеріалом.
  const RECIPES = {
    'allied-worn': [
      ['Світла фарба', 'allied-light', 'color spec', 1, null],
      ['Темні плями фарби', 'allied-dark', 'color spec', 0.35, ['noise', { scale: 1.5, coverage: 0.35, contrast: 0.3 }]],
      ['Гола сталь на краях', 'bare-steel', PAINT, 1, ['edges', { width: 3, contrast: 0.6, breakup: 0.55, scale: 1 }]],
      ['Бруд у швах', 'dirt', PAINT, 0.7, ['cavity', { width: 4, contrast: 0.3, spread: 3 }]]
    ],
    'soviet-rusty': [
      ['Червона фарба', 'soviet-red', 'color spec', 1, null],
      ['Темна сталь на краях', 'soviet-steel', PAINT, 1, ['edges', { width: 3.5, contrast: 0.5, breakup: 0.6, scale: 1.25 }]],
      ['Іржа у швах', 'rust', PAINT, 1, ['cavity', { width: 4, contrast: 0.35, spread: 12, breakup: 0.45, scale: 0.75 }]],
      ['Плями іржі', 'rust', PAINT, 0.8, ['noise', { scale: 0.75, coverage: 0.15, contrast: 0.6 }]],
      ['Бруд знизу', 'dirt', PAINT, 0.65, ['gradient', { direction: 'bottom', offset: 0.35, softness: 0.5 }]]
    ],
    'empire-clean': [
      ['Біла броня', 'empire-white', 'color spec', 1, null],
      ['Графіт у швах', 'empire-graphite', PAINT, 0.85, ['cavity', { width: 3, contrast: 0.6 }]],
      ['Варіація блиску', 'gloss-up', 'spec', 0.5, ['noise', { scale: 2, coverage: 0.5, contrast: 0.2 }], { spec: 110 }]
    ],
    'bare-metal': [
      ['Гола сталь', 'bare-steel', 'color spec', 1, null],
      ['Тьмяні плями', 'bare-steel', 'color spec', 0.6, ['noise', { scale: 1, coverage: 0.45, contrast: 0.25 }], { color: [150, 150, 144], spec: 120 }],
      ['Подряпини', 'bare-steel', 'color spec', 0.8, ['noise', { scale: 0.25, coverage: 0.18, contrast: 0.85, stretch: 1 }], { color: [226, 226, 220], spec: 255 }],
      ['Блискучі краї', 'bare-steel', 'color spec', 1, ['edges', { width: 2, contrast: 0.7, breakup: 0.4, scale: 2 }], { color: [226, 226, 220], spec: 255 }],
      ['Бруд у швах', 'dirt', PAINT, 0.5, ['cavity', { width: 3, contrast: 0.4 }]]
    ],
    'concrete-building': [
      ['Бетон', 'concrete', 'color spec', 1, null],
      ['Плями бетону', 'concrete', 'color spec', 0.5, ['noise', { scale: 0.7, coverage: 0.5, contrast: 0.2 }], { color: [146, 144, 136], spec: 6 }],
      ['Бруд у швах', 'dirt', PAINT, 0.8, ['cavity', { width: 4, contrast: 0.4, spread: 6, breakup: 0.3, scale: 1 }]],
      ['Патьоки', 'dirt', PAINT, 0.25, ['noise', { scale: 0.75, coverage: 0.3, contrast: 0.5, stretch: 0.8 }]],
      ['Бруд знизу', 'dirt', PAINT, 0.6, ['gradient', { direction: 'bottom', offset: 0.3, softness: 0.5 }]]
    ],
    'team-stripes-worn': [
      ['Фарба гравця', 'team-paint', 'color spec team', 1, ['team', { grow: 0, blur: 0 }]],
      ['Знос до сталі', 'bare-steel', PAINT, 1, ['edges', { width: 3, contrast: 0.6, breakup: 0.6, scale: 1 }]],
      ['Бруд у швах', 'dirt', PAINT, 0.5, ['cavity', { width: 3, contrast: 0.4 }]]
    ]
  };

  // Прості специфікації шарів (без буферів), знизу вгору; layers.js перетворює їх на шари.
  // Seed кожної маски береться з mulberry32(seed) по черзі, тож той самий seed дає ті самі маски.
  function smartLayers(id, seed) {
    const recipe = Object.prototype.hasOwnProperty.call(RECIPES, id) ? RECIPES[id] : null;
    if (!recipe) throw new Error('Невідомий розумний матеріал: ' + id);
    const rng = util.mulberry32(Number.isFinite(seed) ? seed : Math.floor(Math.random() * 4294967296));
    return recipe.map(([name, matId, ch, opacity, gen, over]) => {
      const material = Object.assign(copyMaterial(find(matId).material), over || {});
      material.color = material.color.slice();
      const spec = { kind: 'fill', name, material, channels: channels(ch), heightMode: 'add', opacity };
      if (gen) spec.mask = { invert: false, gen: { type: gen[0], seed: (rng() * 4294967296) >>> 0, params: Object.assign({}, gen[1]) } };
      return spec;
    });
  }

  const api = { CHANNELS, list, get, smart, smartLayers };
  RA3.materials = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
