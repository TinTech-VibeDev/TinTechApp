// Geometry and input regression tests; no browser or npm installation required.
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/tv-navigation.js'), 'utf8');

function fixture() {
  let keyListener, observer, modal = null;
  const items = [];
  const document = {
    activeElement: null, body: {}, head: {appendChild() {}},
    createElement() { return {}; },
    addEventListener(name, fn) { if (name === 'keydown') keyListener = fn; },
    querySelectorAll(selector) { return selector.includes('[role=dialog]') ? (modal ? [modal] : []) : items; }
  };
  function element(id, x, y, tag = 'button') {
    const attributes = {};
    const el = {
      id, tag, isConnected: true, disabled: false, hidden: false, clicks: 0,
      getBoundingClientRect: () => ({left:x, top:y, width:120, height:70, right:x+120, bottom:y+70}),
      closest: () => null, querySelector: () => null,
      getAttribute: key => attributes[key] ?? null,
      hasAttribute: key => key in attributes,
      setAttribute: (key, value) => { attributes[key] = value; },
      matches: selector => selector.split(',').some(s => s === tag || (s === '[data-filmbuff-focus]' && 'data-filmbuff-focus' in attributes)),
      focus() { document.activeElement = el; }, scrollIntoView() {}, click() { el.clicks++; }
    };
    items.push(el); return el;
  }
  const a = element('a', 0, 0), b = element('b', 150, 0), c = element('c', 0, 100), d = element('d', 150, 100);
  const hidden = element('hidden', 125, 0); hidden.hidden = true;
  const input = element('search', 320, 0, 'input');
  const window = {};
  vm.runInNewContext(source, {
    document, window, innerHeight:720,
    getComputedStyle: el => ({display:el.hidden ? 'none' : 'block', visibility:'visible', opacity:'1'}),
    matchMedia: () => ({matches:true}), requestAnimationFrame: fn => fn(), setTimeout: fn => fn(),
    MouseEvent: class {}, MutationObserver: class { constructor(fn) { observer = fn; } observe() {} }
  });
  function key(name) { const e = {key:name, prevented:false, preventDefault() { this.prevented = true; }}; keyListener(e); return e; }
  return {a,b,c,d,input,document,api:window.FilmBuffTV,key,element,
    openDialog() {
      const choice = element('choice', 350, 200);
      modal = {isConnected:true, matches:() => false, closest:() => null,
        getBoundingClientRect:() => ({left:100,top:100,width:500,height:400}),
        querySelectorAll:() => [choice], querySelector:() => null,
        dispatchEvent() { choice.isConnected = false; modal = null; observer(); }};
      observer(); return choice;
    }
  };
}

test('remote navigation follows the grid and skips hidden controls', () => {
  const f = fixture(); f.api.focusFirst(); assert.equal(f.document.activeElement, f.a);
  for (const [key, expected] of [['ArrowRight',f.b],['ArrowDown',f.d],['ArrowLeft',f.c],['ArrowUp',f.a]]) {
    assert.equal(f.key(key).prevented, true); assert.equal(f.document.activeElement, expected);
  }
  assert.equal(f.key('Enter').prevented, true); assert.equal(f.a.clicks, 1);
});
test('top edge and editable fields retain native keyboard navigation', () => {
  const f = fixture(); f.a.focus(); assert.equal(f.key('ArrowUp').prevented, false);
  f.input.focus(); assert.equal(f.key('ArrowRight').prevented, false); assert.equal(f.key('Enter').prevented, false);
});
test('focus stays inside a dialog and returns after closing', () => {
  const f = fixture(); f.b.focus(); const choice = f.openDialog();
  assert.equal(f.document.activeElement, choice);
  f.key('ArrowLeft'); assert.equal(f.document.activeElement, choice);
  assert.equal(f.api.closeModal(), true); assert.equal(f.document.activeElement, f.b);
  assert.equal(f.api.closeModal(), false);
});
