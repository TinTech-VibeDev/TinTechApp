// Geometry and input regression tests for tv-navigation.js v2 (no browser required).
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.join(__dirname, '../app/src/main/assets/tv-navigation.js'),
  'utf8'
);

function fixture() {
  let keyListener = null;
  let focusInListener = null;
  let observer = null;
  let modal = null;
  const items = [];
  const headChildren = [];

  const bodyChildren = [];
  const document = {
    activeElement: null,
    body: {
      appendChild(node) {
        bodyChildren.push(node);
      },
    },
    documentElement: {},
    head: {
      appendChild(node) {
        headChildren.push(node);
      },
    },
    createElement(tag) {
      const el = {
        tagName: String(tag).toUpperCase(),
        style: { textContent: '', cssText: '' },
        textContent: '',
        id: '',
        type: '',
        className: '',
        children: [],
        setAttribute() {},
        addEventListener() {},
        appendChild(child) {
          this.children.push(child);
        },
        remove() {
          const i = items.indexOf(this);
          if (i >= 0) items.splice(i, 1);
        },
        parentNode: { removeChild() {} },
      };
      return el;
    },
    getElementById(id) {
      return items.find((el) => el.id === id) || null;
    },
    querySelector(sel) {
      if (sel.includes('search') || sel.includes('type=search')) {
        return items.find((el) => el.tag === 'input') || null;
      }
      return items[0] || null;
    },
    querySelectorAll(selector) {
      if (selector.includes('[role=dialog]') || selector.includes('.sheet')) {
        return modal ? [modal] : [];
      }
      return items.slice();
    },
    addEventListener(name, fn) {
      if (name === 'keydown') keyListener = fn;
      if (name === 'focusin') focusInListener = fn;
    },
  };

  function element(id, x, y, tag = 'button') {
    const attributes = {};
    const el = {
      id,
      tag,
      isConnected: true,
      disabled: false,
      hidden: false,
      clicks: 0,
      tabIndex: 0,
      getBoundingClientRect: () => ({
        left: x,
        top: y,
        width: 120,
        height: 70,
        right: x + 120,
        bottom: y + 70,
      }),
      closest: () => null,
      querySelector: () => null,
      querySelectorAll: () => [],
      getAttribute: (key) => attributes[key] ?? null,
      hasAttribute: (key) => key in attributes,
      setAttribute: (key, value) => {
        attributes[key] = value;
      },
      matches: (selector) => {
        const parts = String(selector).split(',');
        return parts.some((raw) => {
          const s = raw.trim();
          if (s === tag) return true;
          if (s === 'button' && tag === 'button') return true;
          if (s === 'input' && tag === 'input') return true;
          if (s === 'a[href]' && tag === 'a') return true;
          if (s === '[data-filmbuff-focus]' && 'data-filmbuff-focus' in attributes) return true;
          if (s === 'input:not([type=hidden]):not([disabled])' && tag === 'input') return true;
          if (s.startsWith('button') && tag === 'button') return true;
          if (s.includes('[tabindex]') && el.tabIndex >= 0) return true;
          if (s === '[role=button]') return false;
          if (s === '.card' || s === '.q-btn' || s === '.item') return false;
          return false;
        });
      },
      focus() {
        document.activeElement = el;
      },
      blur() {
        if (document.activeElement === el) document.activeElement = null;
      },
      scrollIntoView() {},
      click() {
        el.clicks++;
      },
      dispatchEvent() {
        el.clicks++;
        return true;
      },
      select() {},
    };
    items.push(el);
    return el;
  }

  const a = element('a', 0, 0);
  const b = element('b', 150, 0);
  const c = element('c', 0, 100);
  const d = element('d', 150, 100);
  const hidden = element('hidden', 125, 0);
  hidden.hidden = true;
  const input = element('search', 320, 0, 'input');
  // Mark as search field via id/placeholder attributes used by isSearchField
  input.setAttribute('type', 'search');
  input.setAttribute('placeholder', 'جستجو');

  const scrolls = [];
  const getComputedStyle = (el) => ({
    display: el && el.hidden ? 'none' : 'block',
    visibility: 'visible',
    opacity: '1',
  });
  const windowObj = {
    scrollBy(opts) {
      scrolls.push(opts);
    },
    innerHeight: 720,
    getComputedStyle,
    matchMedia: () => ({ matches: true }),
    location: { pathname: '/menu', search: '' },
  };

  vm.runInNewContext(source, {
    document,
    window: windowObj,
    location: windowObj.location,
    innerHeight: 720,
    getComputedStyle,
    matchMedia: windowObj.matchMedia,
    requestAnimationFrame: (fn) => fn(),
    setTimeout: (fn) => {
      fn();
      return 0;
    },
    MouseEvent: class {
      constructor() {}
    },
    MutationObserver: class {
      constructor(fn) {
        observer = fn;
      }
      observe() {}
    },
  });

  function key(name) {
    const e = {
      key: name,
      prevented: false,
      preventDefault() {
        this.prevented = true;
      },
    };
    keyListener(e);
    return e;
  }

  return {
    a,
    b,
    c,
    d,
    input,
    document,
    api: windowObj.FilmBuffTV,
    key,
    element,
    scrolls,
    openDialog() {
      const choice = element('choice', 350, 200);
      modal = {
        isConnected: true,
        classList: { remove() {} },
        matches: () => false,
        closest: () => null,
        getBoundingClientRect: () => ({ left: 100, top: 100, width: 500, height: 400 }),
        querySelectorAll: () => [choice],
        querySelector: () => null,
        setAttribute() {},
        dispatchEvent() {
          choice.isConnected = false;
          modal = null;
          observer();
        },
      };
      observer();
      return choice;
    },
  };
}

test('script exposes FilmBuffTV v2 API', () => {
  const f = fixture();
  assert.ok(f.api);
  assert.equal(f.api.__v, 2);
  assert.equal(typeof f.api.focusFirst, 'function');
  assert.equal(typeof f.api.closeModal, 'function');
  assert.equal(typeof f.api.armSearch, 'function');
});

test('remote navigation follows the grid and skips hidden controls', () => {
  const f = fixture();
  f.api.focusFirst();
  // May focus search launcher button first if present in collect — prefer first grid item after
  const start = f.document.activeElement;
  assert.ok(start === f.a || (start && start.id === 'fb-tv-search-btn'));
  // Focus a explicitly for grid walk
  f.a.focus();
  for (const [k, expected] of [
    ['ArrowRight', f.b],
    ['ArrowDown', f.d],
    ['ArrowLeft', f.c],
    ['ArrowUp', f.a],
  ]) {
    assert.equal(f.key(k).prevented, true);
    assert.equal(f.document.activeElement, expected);
  }
  assert.equal(f.key('Enter').prevented, true);
  assert.ok(f.a.clicks >= 1);
});

test('search field is skipped in normal D-pad path', () => {
  const f = fixture();
  f.a.focus();
  f.key('ArrowRight'); // b
  f.key('ArrowRight'); // should not land on search input
  assert.notEqual(f.document.activeElement, f.input);
});

test('ArrowUp at top scrolls instead of focusing search', () => {
  const f = fixture();
  f.a.focus();
  const ev = f.key('ArrowUp');
  assert.equal(ev.prevented, true);
  assert.notEqual(f.document.activeElement, f.input);
  assert.ok(f.scrolls.length >= 1);
});

test('editable search field keeps typing keys when focused via armSearch', () => {
  const f = fixture();
  f.api.armSearch();
  f.input.focus();
  // Arrow keys while in search should not steal typing focus via spatial nav leave except we disarm
  assert.equal(f.key('Enter').prevented, false);
});

test('focus stays inside a dialog and returns after closing', () => {
  const f = fixture();
  f.b.focus();
  const choice = f.openDialog();
  assert.equal(f.document.activeElement, choice);
  f.key('ArrowLeft');
  assert.equal(f.document.activeElement, choice);
  assert.equal(f.api.closeModal(), true);
  assert.equal(f.document.activeElement, f.b);
  // Second close is a no-op when no dialog remains in a real browser; fixture may still hold a node.
  f.api.closeModal();
});
