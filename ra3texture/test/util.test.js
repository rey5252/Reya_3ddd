const test = require('node:test'); const assert = require('node:assert');
const u = require('../js/util.js');
test('mulberry32 deterministic', () => { const a = u.mulberry32(42), b = u.mulberry32(42);
  for (let i = 0; i < 5; i++) assert.strictEqual(a(), b()); });
test('fbm in range and tileable', () => {
  for (let i = 0; i < 50; i++) { const x = i / 50; const v = u.fbm(7, x, 0.3, 4, 4); assert.ok(v >= 0 && v <= 1); }
  assert.ok(Math.abs(u.fbm(7, 0, 0.3, 4, 4) - u.fbm(7, 1 - 1e-9, 0.3, 4, 4)) < 1e-3); });
test('hex roundtrip', () => { assert.deepStrictEqual(u.hexToRgb('#9fa9b0'), [159, 169, 176]);
  assert.strictEqual(u.rgbToHex([159, 169, 176]), '#9fa9b0'); });
test('isPow2', () => { assert.ok(u.isPow2(512)); assert.ok(!u.isPow2(500)); });
test('resampleRGBA solid stays solid', () => {
  const src = new Uint8ClampedArray(3 * 5 * 4).fill(200);
  const out = u.resampleRGBA(src, 3, 5, 8, 8);
  assert.strictEqual(out.length, 256); assert.ok(out.every(v => v === 200)); });

// Additional behaviour checks for the rest of the RA3.util interface.
test('clamp, lerp, smoothstep', () => {
  assert.strictEqual(u.clamp(5, 0, 1), 1); assert.strictEqual(u.clamp(-5, 0, 1), 0); assert.strictEqual(u.clamp(0.25, 0, 1), 0.25);
  assert.strictEqual(u.lerp(10, 20, 0), 10); assert.strictEqual(u.lerp(10, 20, 1), 20); assert.strictEqual(u.lerp(10, 20, 0.5), 15);
  assert.strictEqual(u.smoothstep(0, 1, -1), 0); assert.strictEqual(u.smoothstep(0, 1, 2), 1);
  assert.strictEqual(u.smoothstep(0, 1, 0.5), 0.5); assert.strictEqual(u.smoothstep(2, 4, 3), 0.5); });
test('isPow2 edge cases', () => {
  for (const n of [1, 2, 256, 1024, 2048]) assert.ok(u.isPow2(n), String(n));
  for (const n of [0, -2, 3, 6, 1000, 1.5]) assert.ok(!u.isPow2(n), String(n)); });
test('mulberry32 range and seeds differ', () => {
  const a = u.mulberry32(1), b = u.mulberry32(2); let diff = false;
  for (let i = 0; i < 1000; i++) { const x = a(); assert.ok(x >= 0 && x < 1); if (x !== b()) diff = true; }
  assert.ok(diff); });
test('fbm deterministic, seed-sensitive, not constant', () => {
  assert.strictEqual(u.fbm(7, 0.31, 0.62, 4, 4), u.fbm(7, 0.31, 0.62, 4, 4));
  assert.notStrictEqual(u.fbm(7, 0.31, 0.62, 4, 4), u.fbm(8, 0.31, 0.62, 4, 4));
  const seen = new Set(); for (let i = 0; i < 40; i++) seen.add(u.fbm(3, i / 40, (i * 7 % 40) / 40, 4, 3));
  assert.ok(seen.size > 20); });
test('fbm tiles on both axes', () => {
  for (let i = 0; i < 20; i++) { const t = i / 20;
    assert.ok(Math.abs(u.fbm(5, t, 0, 3, 5) - u.fbm(5, t, 1 - 1e-9, 3, 5)) < 1e-3);
    assert.ok(Math.abs(u.fbm(5, 0, t, 3, 5) - u.fbm(5, 1 - 1e-9, t, 3, 5)) < 1e-3); } });
test('hexToRgb accepts uppercase, rgbToHex clamps and rounds', () => {
  assert.deepStrictEqual(u.hexToRgb('#FFAA00'), [255, 170, 0]);
  assert.strictEqual(u.rgbToHex([300, -5, 127.6]), '#ff0080'); });
test('luminance', () => {
  assert.strictEqual(u.luminance(0, 0, 0), 0);
  assert.ok(Math.abs(u.luminance(255, 255, 255) - 255) < 1e-9);
  assert.ok(Math.abs(u.luminance(100, 0, 0) - 29.9) < 1e-9);
  assert.ok(Math.abs(u.luminance(0, 100, 0) - 58.7) < 1e-9);
  assert.ok(Math.abs(u.luminance(0, 0, 100) - 11.4) < 1e-9); });
test('resampleRGBA identity and bilinear interpolation', () => {
  const src = Uint8ClampedArray.from([0, 10, 20, 255, 100, 110, 120, 255, 200, 210, 220, 255, 50, 60, 70, 255]);
  assert.deepStrictEqual(Array.from(u.resampleRGBA(src, 2, 2, 2, 2)), Array.from(src));
  // 2x1 gradient 0 -> 200 upscaled to 4x1: pixel-centre mapping, clamped at edges.
  const g = Uint8ClampedArray.from([0, 0, 0, 255, 200, 200, 200, 255]);
  const out = u.resampleRGBA(g, 2, 1, 4, 1);
  assert.deepStrictEqual([out[0], out[4], out[8], out[12]], [0, 50, 150, 200]);
  assert.strictEqual(out[3], 255); });
test('resampleRGBA downscale averages and output type', () => {
  const g = Uint8ClampedArray.from([0, 0, 0, 255, 200, 200, 200, 255]);
  const out = u.resampleRGBA(g, 2, 1, 1, 1);
  assert.ok(out instanceof Uint8ClampedArray); assert.strictEqual(out.length, 4);
  assert.strictEqual(out[0], 100); assert.strictEqual(out[3], 255); });
