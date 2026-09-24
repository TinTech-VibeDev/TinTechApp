(function () {
  'use strict';
  if (window.FilmBuffTV) return;
  var style = document.createElement('style');
  style.textContent = `
    html { scroll-behavior:smooth; scroll-padding:32px 0 100px; }
    body { font-size:18px!important; }
    .app { max-width:1500px!important; padding:0 36px!important; margin:auto; }
    .top { padding-top:22px!important; }
    .grid { grid-template-columns:repeat(5,minmax(0,1fr))!important; gap:26px 20px!important; }
    .ct { font-size:17px!important; line-height:1.7!important; }
    .sec-h h2 { font-size:25px!important; }
    .nav { max-width:1000px!important; padding:8px 16px!important; bottom:16px!important; }
    .nav button { font-size:16px!important; min-height:58px!important; }
    .btn,.q-btn,.item,.more,.ep,.country-card { font-size:18px!important; min-height:48px; }
    input,select { font-size:20px!important; min-height:52px; }
    .sheet { align-items:center!important; padding:24px!important; }
    .sheet .panel { max-width:1050px!important; max-height:88vh!important; border-radius:24px!important; }
    .body { font-size:19px!important; }.body h2 { font-size:30px!important; }
    .ov { font-size:18px!important; }.q-grid { grid-template-columns:repeat(3,minmax(0,1fr))!important; }
    .pop-card { max-width:880px!important; max-height:80vh!important; overflow-y:auto!important; font-size:20px!important; }
    [data-filmbuff-focus]:focus,button:focus,a:focus,input:focus,select:focus,[tabindex]:focus {
      outline:3px solid #b9f6e1!important; outline-offset:4px!important;
      box-shadow:0 0 0 7px #b9f6e124!important; transition:box-shadow .18s ease,transform .18s ease;
    }
    .card:focus .ph { transform:translateY(-3px); }
    @media(min-width:1500px){.grid{grid-template-columns:repeat(6,minmax(0,1fr))!important}}
    @media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}*{transition:none!important;animation:none!important}}
  `;
  document.head.appendChild(style);
  var selector = 'a[href],button,input:not([type=hidden]),select,textarea,[role=button],[tabindex],.card,.q-btn,.item,[onclick]';
  var lastFocus = null, lastScope = document;
  function scope() {
    var candidates = Array.from(document.querySelectorAll('[role=dialog],.sheet.open,.pop.open,.pop.show,.pop-card'))
      .map(function (el) { return el.matches('.pop-card') ? el.parentElement : el; });
    return candidates.filter(visible).pop() || document;
  }
  function visible(el) {
    if (!el || !el.isConnected || el.disabled || el.closest('[hidden],[aria-hidden=true]')) return false;
    var css = getComputedStyle(el), rect = el.getBoundingClientRect();
    return css.display !== 'none' && css.visibility !== 'hidden' && Number(css.opacity) > 0 && rect.width > 0 && rect.height > 0;
  }
  function collect() {
    return Array.from(scope().querySelectorAll(selector)).filter(function (el) {
      if (!visible(el) || el.getAttribute('tabindex') === '-1') return false;
      if (el.matches('.card,.item,[onclick]') && el.querySelector('button,a,input,select')) return false;
      if (!el.hasAttribute('tabindex') && !el.matches('a,button,input,select,textarea')) el.tabIndex = 0;
      el.setAttribute('data-filmbuff-focus', '');
      return true;
    });
  }
  function focus(el) {
    if (!el) return false;
    el.focus({preventScroll:true});
    el.scrollIntoView({block:'nearest',inline:'nearest',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'});
    return true;
  }
  function focusFirst() {
    var list = collect(), active = document.activeElement;
    return focus(list.includes(active) ? active : list.find(function (el) {
      var r = el.getBoundingClientRect(); return r.bottom > 0 && r.top < innerHeight;
    }) || list[0]);
  }
  function closeModal() {
    var modal = scope(); if (modal === document) return false;
    var close = modal.querySelector('#cx,#cx2,[data-close],.close,.pop-close,button[aria-label="بستن"]');
    if (close) close.click();
    else modal.dispatchEvent(new MouseEvent('click', {bubbles:true}));
    setTimeout(function () { if (lastFocus && visible(lastFocus)) focus(lastFocus); }, 200);
    return true;
  }
  document.addEventListener('keydown', function (event) {
    var active = document.activeElement;
    // Text entry, native selects and sliders keep their normal keyboard/remote behavior.
    if (active && active.matches('input,textarea,select,[contenteditable=true]')) return;
    var key = event.key;
    if (key === 'Enter' || key === ' ') {
      if (active && visible(active) && active.matches('[data-filmbuff-focus]')) { event.preventDefault(); active.click(); }
      else { event.preventDefault(); focusFirst(); }
      return;
    }
    if (!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(key)) return;
    var list = collect(); if (!list.length) return;
    if (!list.includes(active)) { event.preventDefault(); focusFirst(); return; }
    var r = active.getBoundingClientRect(), x = r.left + r.width / 2, y = r.top + r.height / 2;
    var horizontal = key === 'ArrowLeft' || key === 'ArrowRight', sign = key === 'ArrowLeft' || key === 'ArrowUp' ? -1 : 1;
    var best, score = Infinity;
    list.forEach(function (el) {
      if (el === active) return;
      var b = el.getBoundingClientRect(), dx = b.left + b.width / 2 - x, dy = b.top + b.height / 2 - y;
      var forward = (horizontal ? dx : dy) * sign, cross = Math.abs(horizontal ? dy : dx);
      if (forward < 4) return;
      var overlap = horizontal ? b.top < r.bottom && b.bottom > r.top : b.left < r.right && b.right > r.left;
      var value = forward + cross * (overlap ? 1.5 : 4) + (overlap ? 0 : 200);
      if (value < score) { score = value; best = el; }
    });
    if (best) { event.preventDefault(); focus(best); }
  }, true);
  var scheduled = false;
  new MutationObserver(function () {
    if (scheduled) return; scheduled = true;
    requestAnimationFrame(function () {
      scheduled = false;
      var next = scope();
      if (next !== lastScope) {
        if (next !== document) lastFocus = document.activeElement;
        lastScope = next;
        if (next === document && visible(lastFocus)) focus(lastFocus); else focusFirst();
      }
      collect();
    });
  }).observe(document.body, {childList:true,subtree:true,attributes:true,attributeFilter:['class','hidden','aria-hidden']});
  window.FilmBuffTV = {focusFirst:focusFirst,closeModal:closeModal};
  collect();
})();
