(function () {
  'use strict';
  if (window.FilmBuffTV && window.FilmBuffTV.__v >= 2) return;

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
    .sheet.open,.sheet.show,[role=dialog][open],.pop.open,.pop.show {
      display:flex!important; visibility:visible!important; opacity:1!important; pointer-events:auto!important; z-index:9999!important;
    }
    .sheet .panel { max-width:1050px!important; max-height:88vh!important; border-radius:24px!important; overflow:auto!important; }
    .body { font-size:19px!important; }.body h2 { font-size:30px!important; }
    .ov { font-size:18px!important; }.q-grid { grid-template-columns:repeat(3,minmax(0,1fr))!important; }
    .pop-card { max-width:880px!important; max-height:80vh!important; overflow-y:auto!important; font-size:20px!important; }
    [data-filmbuff-focus]:focus,button:focus,a:focus,input:focus,select:focus,[tabindex]:focus,
    #fb-tv-search-btn:focus {
      outline:3px solid #b9f6e1!important; outline-offset:4px!important;
      box-shadow:0 0 0 7px #b9f6e124!important; transition:box-shadow .18s ease,transform .18s ease;
    }
    .card:focus .ph { transform:translateY(-3px); }
    #fb-tv-search-btn {
      position:fixed; top:18px; left:18px; z-index:10050;
      min-height:56px; padding:12px 18px; border-radius:14px; border:2px solid #b9f6e1;
      background:linear-gradient(135deg,#1a2433,#0e141c); color:#e8fff7; font-size:16px; font-weight:700;
      cursor:pointer; box-shadow:0 8px 28px rgba(0,0,0,.45); max-width:42vw; line-height:1.4;
    }
    #fb-tv-search-btn:focus { transform:scale(1.04); background:linear-gradient(135deg,#243246,#15202c); }
    #fb-tv-player-tip {
      position:fixed; inset:auto 8% 12%; z-index:10060; padding:18px 22px; border-radius:16px;
      background:rgba(10,14,20,.94); border:1px solid rgba(185,246,225,.45); color:#f2f7f5;
      font-size:18px; line-height:1.75; text-align:center; box-shadow:0 12px 40px rgba(0,0,0,.5);
      pointer-events:none;
    }
    @media(min-width:1500px){.grid{grid-template-columns:repeat(6,minmax(0,1fr))!important}}
    @media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}*{transition:none!important;animation:none!important}}
  `;
  document.head.appendChild(style);

  var selector = 'a[href],button:not([disabled]),input:not([type=hidden]):not([disabled]),select:not([disabled]),textarea:not([disabled]),[role=button],[tabindex]:not([tabindex="-1"]),.card,.q-btn,.item,.more,.ep,.country-card,[onclick]';
  var lastFocus = null, lastScope = document, searchArmed = false, tipShownFor = '';

  function isSearchField(el) {
    if (!el || !el.matches) return false;
    if (!el.matches('input,textarea')) return false;
    var t = (el.getAttribute('type') || '').toLowerCase();
    if (t === 'search') return true;
    var id = (el.id || '') + ' ' + (el.name || '') + ' ' + (el.className || '') + ' ' + (el.getAttribute('placeholder') || '');
    return /search|جست|سرچ|query|q\b/i.test(id);
  }

  function isSearchLauncher(el) {
    return el && el.id === 'fb-tv-search-btn';
  }

  function visible(el) {
    if (!el || !el.isConnected || el.disabled || el.getAttribute('aria-hidden') === 'true') return false;
    var st = window.getComputedStyle(el);
    if (st.display === 'none' || st.visibility === 'hidden' || Number(st.opacity) === 0) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    // allow slightly off-screen for scroll targets
    if (r.bottom < -20 || r.top > (window.innerHeight + 20)) return false;
    return true;
  }

  function scope() {
    var candidates = Array.from(document.querySelectorAll(
      '[role=dialog],.sheet.open,.sheet.show,.sheet[aria-hidden=false],.pop.open,.pop.show,.modal.open,.modal.show,.panel.open,[data-open=true],.pop-card'
    )).map(function (el) {
      if (el.matches && el.matches('.pop-card')) return el.parentElement || el;
      return el;
    });
    var open = candidates.filter(function (el) {
      if (!el || !el.isConnected) return false;
      var st = window.getComputedStyle(el);
      if (st.display === 'none' || st.visibility === 'hidden') return false;
      return true;
    });
    return open.pop() || document;
  }

  function collect(root) {
    root = root || scope();
    var list = Array.from(root.querySelectorAll(selector));
    if (root !== document && root.matches && root.matches(selector)) list.unshift(root);
    // always include search launcher when on main document scope
    var btn = document.getElementById('fb-tv-search-btn');
    if (root === document && btn && list.indexOf(btn) < 0) list.unshift(btn);
    return list.filter(function (el) {
      if (!visible(el)) return false;
      if (el.tabIndex < -1) return false;
      // Search fields are NOT in normal D-pad path unless armed by the side button
      if (isSearchField(el) && !searchArmed) return false;
      return true;
    });
  }

  function focus(el) {
    if (!el) return false;
    try {
      el.setAttribute('data-filmbuff-focus', '1');
      if (typeof el.focus === 'function') el.focus({ preventScroll: false });
      else el.focus();
      el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
      return true;
    } catch (e) { return false; }
  }

  function focusFirst(root) {
    var list = collect(root);
    if (!list.length) return false;
    // prefer primary action buttons inside modals
    var preferred = list.find(function (el) {
      return el.matches && el.matches('.btn.main,.btn.primary,button.primary,[data-primary],.q-btn,.item');
    });
    return focus(preferred || list[0]);
  }

  function findSearchField() {
    var inputs = Array.from(document.querySelectorAll('input,textarea'));
    return inputs.find(isSearchField) || document.querySelector('input[type=search],input[name=q],#search,.search input');
  }

  function armSearch() {
    searchArmed = true;
    var field = findSearchField();
    if (field) {
      try { field.scrollIntoView({ block: 'center', behavior: 'smooth' }); } catch (e) {}
      setTimeout(function () {
        try {
          field.focus();
          if (typeof field.select === 'function') field.select();
        } catch (e) {}
      }, 80);
    } else {
      searchArmed = false;
    }
  }

  function ensureSearchButton() {
    if (document.getElementById('fb-tv-search-btn')) return;
    var btn = document.createElement('button');
    btn.id = 'fb-tv-search-btn';
    btn.type = 'button';
    btn.textContent = 'برای سرچ کردن کلیک کنید';
    btn.setAttribute('data-filmbuff-focus', '1');
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      armSearch();
    });
    document.body.appendChild(btn);
  }

  function closeModal() {
    var s = scope();
    if (s === document) return false;
    var closer = s.querySelector('[data-close],.close,.sheet-close,button[aria-label*=close i],button[aria-label*=بستن]');
    if (closer) { closer.click(); return true; }
    s.classList.remove('open', 'show');
    s.setAttribute('aria-hidden', 'true');
    searchArmed = false;
    if (visible(lastFocus)) focus(lastFocus);
    return true;
  }

  function activate(el) {
    if (!el) return;
    if (isSearchLauncher(el)) { armSearch(); return; }
    // Programmatic activation that works for remote Enter on TV WebView
    try {
      if (typeof el.click === 'function') el.click();
    } catch (e) {}
    try {
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
    } catch (e2) {
      try {
        var ev = document.createEvent('MouseEvents');
        ev.initEvent('click', true, true);
        el.dispatchEvent(ev);
      } catch (e3) {}
    }
    // After card/detail actions, modal may open — refocus shortly
    setTimeout(function () {
      var next = scope();
      if (next !== document) {
        lastScope = next;
        focusFirst(next);
      }
    }, 120);
    setTimeout(function () {
      var next = scope();
      if (next !== document) {
        lastScope = next;
        focusFirst(next);
      }
    }, 400);
  }

  function spatialNav(active, key) {
    var list = collect();
    if (!list.length) return null;
    if (!list.includes(active)) return list[0];
    var r = active.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var horizontal = key === 'ArrowLeft' || key === 'ArrowRight';
    var sign = key === 'ArrowLeft' || key === 'ArrowUp' ? -1 : 1;
    var best = null, score = Infinity;
    list.forEach(function (el) {
      if (el === active) return;
      // never spatial-jump onto search field during normal nav
      if (isSearchField(el)) return;
      var b = el.getBoundingClientRect();
      var dx = b.left + b.width / 2 - x;
      var dy = b.top + b.height / 2 - y;
      var forward = (horizontal ? dx : dy) * sign;
      var cross = Math.abs(horizontal ? dy : dx);
      if (forward < 4) return;
      var overlap = horizontal ? (b.top < r.bottom && b.bottom > r.top) : (b.left < r.right && b.right > r.left);
      var value = forward + cross * (overlap ? 1.4 : 4) + (overlap ? 0 : 180);
      // Prefer cards over the floating search button when moving vertically among content
      if (isSearchLauncher(el) && !horizontal) value += 120;
      if (value < score) { score = value; best = el; }
    });
    return best;
  }

  function onArrowUpFromTop(active) {
    // If nothing above, scroll page up and focus the highest content card (not search)
    var list = collect().filter(function (el) { return !isSearchField(el) && !isSearchLauncher(el); });
    window.scrollBy({ top: -Math.max(180, window.innerHeight * 0.45), left: 0, behavior: 'smooth' });
    setTimeout(function () {
      var after = collect().filter(function (el) { return !isSearchField(el) && !isSearchLauncher(el); });
      if (!after.length) return;
      // pick topmost in viewport
      var top = after[0];
      var minY = Infinity;
      after.forEach(function (el) {
        var y = el.getBoundingClientRect().top;
        if (y >= -10 && y < minY) { minY = y; top = el; }
      });
      focus(top);
    }, 180);
  }

  document.addEventListener('keydown', function (event) {
    var active = document.activeElement;
    // While typing in search, only allow Escape to leave
    if (active && isSearchField(active)) {
      if (event.key === 'Escape') {
        event.preventDefault();
        searchArmed = false;
        active.blur();
        var btn = document.getElementById('fb-tv-search-btn');
        if (btn) focus(btn); else focusFirst();
      }
      return;
    }
    if (active && active.matches && active.matches('textarea,select,[contenteditable=true]')) return;

    var key = event.key;
    if (key === 'Enter' || key === ' ') {
      if (active && visible(active) && (active.hasAttribute('data-filmbuff-focus') || active.matches(selector) || isSearchLauncher(active))) {
        event.preventDefault();
        activate(active);
      } else {
        event.preventDefault();
        focusFirst();
      }
      return;
    }
    if (key === 'Escape' || key === 'Backspace') {
      if (closeModal()) { event.preventDefault(); return; }
    }
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(key)) return;

    // Leaving search mode on any arrow
    if (searchArmed && active && isSearchField(active)) {
      searchArmed = false;
    }

    var list = collect();
    if (!list.length) return;
    if (!list.includes(active)) {
      event.preventDefault();
      focusFirst();
      return;
    }

    var best = spatialNav(active, key);
    if (best) {
      event.preventDefault();
      focus(best);
      return;
    }

    if (key === 'ArrowUp') {
      event.preventDefault();
      onArrowUpFromTop(active);
    } else if (key === 'ArrowDown') {
      event.preventDefault();
      window.scrollBy({ top: Math.max(160, window.innerHeight * 0.4), left: 0, behavior: 'smooth' });
      setTimeout(function () {
        var after = spatialNav(active, 'ArrowDown') || collect().filter(function (el) { return !isSearchField(el); })[0];
        if (after) focus(after);
      }, 160);
    }
  }, true);

  // Disarm search when user focuses something else via pointer
  document.addEventListener('focusin', function (e) {
    if (e.target && !isSearchField(e.target)) {
      // keep armed only while still on search field
      if (searchArmed && document.activeElement && !isSearchField(document.activeElement)) {
        searchArmed = false;
      }
    }
  }, true);

  function isPlayerChooserPage() {
    try {
      var path = (location.pathname || '').toLowerCase();
      if (path.indexOf('/prepare') >= 0 || path.indexOf('/go') >= 0) return true;
      if (document.getElementById('buttons') && document.getElementById('networkMode')) return true;
      if (document.querySelector('.player-audience-note')) return true;
      if (/پلیر|player/i.test(document.title || '') && document.querySelector('.btns,#buttons')) return true;
    } catch (e) {}
    return false;
  }

  function showPlayerTip() {
    var key = location.pathname + location.search;
    if (tipShownFor === key) return;
    if (!isPlayerChooserPage()) return;
    tipShownFor = key;
    var old = document.getElementById('fb-tv-player-tip');
    if (old) old.remove();
    var tip = document.createElement('div');
    tip.id = 'fb-tv-player-tip';
    tip.setAttribute('role', 'status');
    tip.textContent = 'کاربر گرامی برای استفاده از پلیر از دو پلیر داخلی و VLC استفاده کنید؛ در پلیر پیشنهادی ما پلیر داخلی می‌باشد. با تشکر';
    document.body.appendChild(tip);
    setTimeout(function () {
      if (tip.parentNode) tip.remove();
    }, 10000);
  }

  var scheduled = false;
  function onDomChange() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(function () {
      scheduled = false;
      ensureSearchButton();
      showPlayerTip();
      var next = scope();
      if (next !== lastScope) {
        if (next !== document) lastFocus = document.activeElement;
        lastScope = next;
        if (next === document && visible(lastFocus) && !isSearchField(lastFocus)) focus(lastFocus);
        else focusFirst(next);
      } else if (next !== document) {
        // keep modal usable if focus was lost
        var ae = document.activeElement;
        if (!ae || ae === document.body || !next.contains(ae)) focusFirst(next);
      }
      collect();
    });
  }

  new MutationObserver(onDomChange).observe(document.documentElement, {
    childList: true, subtree: true, attributes: true,
    attributeFilter: ['class', 'hidden', 'aria-hidden', 'open', 'style']
  });

  window.FilmBuffTV = {
    __v: 2,
    focusFirst: focusFirst,
    closeModal: closeModal,
    armSearch: armSearch,
    collect: collect
  };

  ensureSearchButton();
  showPlayerTip();
  collect();
  if (!document.activeElement || document.activeElement === document.body) {
    setTimeout(function () { focusFirst(); }, 50);
  }
})();
