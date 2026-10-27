/*
 * Shared by SecureOne's hosted pages (sign-in, sign-up, password reset, device sign-in, account security):
 *  - Branding: the application's name, logo, colours and corner radius (Application → Settings → Appearance),
 *    from GET /api/v1/applications/{id}/branding. The application comes from ?applicationId= or, during an OAuth
 *    sign-in, from the client_id in ?continue=.
 *  - Language: ?lang=, then OIDC ui_locales, then the person's earlier choice, then the application's default,
 *    then the browser. A picker at the bottom of the page changes it. Texts come from i18n.js; anything not
 *    translated stays in English.
 */
(function () {
  "use strict";

  var I18N = window.SecureOneI18n || { languages: [{ code: "en", name: "English" }], messages: {}, patterns: [] };
  var SUPPORTED = I18N.languages.map(function (l) { return l.code; });
  var STORAGE_KEY = "secureone.lang";
  var params = new URLSearchParams(location.search);
  var continueParams = (function () {
    try {
      var c = params.get("continue");
      return c ? new URL(c, location.origin).searchParams : new URLSearchParams();
    } catch (e) {
      return new URLSearchParams();
    }
  })();

  function pick(list) {
    for (var i = 0; i < list.length; i++) {
      var code = String(list[i] || "").toLowerCase().split(/[-_]/)[0];
      if (SUPPORTED.indexOf(code) >= 0) return code;
    }
    return null;
  }

  function storedChoice() {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch (e) {
      return null;
    }
  }

  /** The language asked for explicitly (URL, OIDC ui_locales or the picker); null when only defaults apply. */
  function explicitLanguage() {
    var uiLocales = (params.get("ui_locales") || continueParams.get("ui_locales") || "").split(/\s+/);
    return pick([params.get("lang")]) || pick(uiLocales) || pick([storedChoice()]);
  }

  var lang = explicitLanguage() || pick(navigator.languages || [navigator.language]) || "en";

  // ---- Translation ---------------------------------------------------------------------------------------------
  function translateCore(text) {
    if (lang === "en") return null;
    var table = I18N.messages[lang] || {};
    if (Object.prototype.hasOwnProperty.call(table, text)) return table[text];
    for (var i = 0; i < I18N.patterns.length; i++) {
      var p = I18N.patterns[i];
      var m = text.match(p.re);
      if (m && p.out[lang]) {
        return p.out[lang].replace(/\$(\d)/g, function (_, n) {
          var part = m[Number(n)] || "";
          return Object.prototype.hasOwnProperty.call(table, part) ? table[part] : part;
        });
      }
    }
    return null;
  }

  /** Translates the text while keeping its leading and trailing spaces; unknown text is returned unchanged. */
  function tr(text) {
    if (!text) return text;
    var core = text.trim();
    if (!core) return text;
    var out = translateCore(core);
    if (out == null) return text;
    var start = text.indexOf(core);
    return text.slice(0, start) + out + text.slice(start + core.length);
  }

  var originals = new WeakMap(); // node -> { en: English text, shown: what we put there }
  var SKIP = { SCRIPT: 1, STYLE: 1, CODE: 1, PRE: 1, TEXTAREA: 1, NOSCRIPT: 1 };

  function translateTextNode(node) {
    var parent = node.parentNode;
    if (!parent || SKIP[parent.nodeName] || (parent.closest && parent.closest("[data-no-translate]"))) return;
    var record = originals.get(node);
    var current = node.nodeValue;
    if (!record || current !== record.shown) {
      record = { en: current, shown: current };
    }
    var next = lang === "en" ? record.en : tr(record.en);
    record.shown = next;
    originals.set(node, record);
    if (current !== next) node.nodeValue = next;
  }

  var ATTRS = ["placeholder", "title", "aria-label"];
  function translateElement(el) {
    if (el.nodeType !== 1 || (el.closest && el.closest("[data-no-translate]"))) return;
    ATTRS.forEach(function (name) {
      if (!el.hasAttribute(name)) return;
      var key = "data-en-" + name;
      var value = el.getAttribute(name);
      if (!el.hasAttribute(key) || (el.getAttribute("data-shown-" + name) !== value)) el.setAttribute(key, value);
      var shown = lang === "en" ? el.getAttribute(key) : tr(el.getAttribute(key));
      el.setAttribute("data-shown-" + name, shown);
      if (value !== shown) el.setAttribute(name, shown);
    });
  }

  function translateTree(root) {
    if (!root) return;
    if (root.nodeType === 3) {
      translateTextNode(root);
      return;
    }
    if (root.nodeType !== 1 || SKIP[root.nodeName]) return;
    translateElement(root);
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
    var node;
    while ((node = walker.nextNode())) {
      if (node.nodeType === 3) translateTextNode(node);
      else translateElement(node);
    }
  }

  var englishTitle = null;
  function translateTitle() {
    if (englishTitle === null || (document.title !== translatedTitle)) englishTitle = document.title;
    translatedTitle = lang === "en" ? englishTitle : tr(englishTitle);
    if (document.title !== translatedTitle) document.title = translatedTitle;
  }
  var translatedTitle = null;

  // Dialogs the pages open themselves ("Remove passkey?", "Request account deletion?").
  var nativeConfirm = window.confirm.bind(window);
  var nativeAlert = window.alert.bind(window);
  window.confirm = function (message) { return nativeConfirm(tr(String(message))); };
  window.alert = function (message) { return nativeAlert(tr(String(message))); };

  function applyLanguage() {
    document.documentElement.lang = lang;
    translateTree(document.body);
    translateTitle();
    var select = document.getElementById("so-lang-select");
    if (select) select.value = lang;
  }

  // ---- Language picker -----------------------------------------------------------------------------------------
  function addPicker() {
    if (I18N.languages.length < 2 || document.getElementById("so-lang")) return;
    var wrap = document.createElement("div");
    wrap.id = "so-lang";
    wrap.className = "so-lang";
    var label = document.createElement("label");
    label.setAttribute("for", "so-lang-select");
    label.textContent = "Language";
    var select = document.createElement("select");
    select.id = "so-lang-select";
    select.setAttribute("data-no-translate", "");
    I18N.languages.forEach(function (l) {
      var o = document.createElement("option");
      o.value = l.code;
      o.textContent = l.name;
      select.appendChild(o);
    });
    select.value = lang;
    select.addEventListener("change", function () {
      lang = select.value;
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch (e) {
        // Private windows: the choice lasts for this page only.
      }
      applyLanguage();
    });
    wrap.appendChild(label);
    wrap.appendChild(select);
    document.body.appendChild(wrap);
  }

  // ---- Branding ------------------------------------------------------------------------------------------------
  var HEX = /^#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;
  function color(value, fallback) {
    return typeof value === "string" && HEX.test(value.trim()) ? value.trim() : fallback;
  }

  function applyBranding(b) {
    if (!b) return;
    var dark = b.mode === "dark" || (b.mode === "system" && window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches);
    var primary = color(b.primaryColor, "#4f46e5");
    var vars = {
      "--so-primary": primary,
      "--so-button-text": color(b.buttonTextColor, "#ffffff"),
      "--so-bg": dark ? color(b.darkBackgroundColor, "#121216") : color(b.backgroundColor, "#ffffff"),
      "--so-surface": dark ? color(b.darkSurfaceColor, "#1c1c24") : color(b.surfaceColor, "#ffffff"),
      "--so-text": dark ? color(b.darkTextColor, "#ededed") : color(b.textColor, "#171717"),
      "--so-input-bg": dark ? color(b.darkSurfaceColor, "#1c1c24") : color(b.inputBackground, "#ffffff"),
      "--so-input-text": dark ? color(b.darkTextColor, "#ededed") : color(b.inputText, "#171717"),
      "--so-input-border": color(b.inputBorder, "#d1d5db"),
      "--so-muted": color(b.mutedText, "#6b7280"),
      "--so-radius": (typeof b.borderRadius === "number" && b.borderRadius >= 0 && b.borderRadius <= 48 ? b.borderRadius : 8) + "px",
    };
    var root = document.documentElement.style;
    Object.keys(vars).forEach(function (k) { root.setProperty(k, vars[k]); });
    if (typeof b.fontScale === "number" && b.fontScale >= 0.75 && b.fontScale <= 1.5) {
      document.documentElement.style.fontSize = b.fontScale * 100 + "%";
    }
    document.documentElement.classList.add("so-branded");

    if (typeof b.logoUrl === "string" && /^https:\/\//i.test(b.logoUrl) && !document.getElementById("so-logo")) {
      var img = document.createElement("img");
      img.id = "so-logo";
      img.className = "so-logo";
      img.src = b.logoUrl;
      img.alt = b.appName || "";
      img.referrerPolicy = "no-referrer";
      img.addEventListener("error", function () { img.remove(); });
      var heading = document.querySelector("h1");
      if (heading && heading.parentNode) heading.parentNode.insertBefore(img, heading);
    }
    // The application's default language applies when nobody asked for one.
    if (!explicitLanguage() && b.language && b.language !== "auto" && SUPPORTED.indexOf(b.language) >= 0 && b.language !== lang) {
      lang = b.language;
      applyLanguage();
    }
  }

  function loadBranding() {
    var appId = params.get("applicationId");
    var clientId = continueParams.get("client_id") || params.get("client_id");
    var resolve = appId
      ? Promise.resolve(appId)
      : clientId
        ? fetch("/api/v1/applications/resolve?clientId=" + encodeURIComponent(clientId))
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (j) { return j && j.applicationId; })
        : Promise.resolve(null);
    resolve
      .then(function (id) {
        if (!id) return null;
        return fetch("/api/v1/applications/" + encodeURIComponent(id) + "/branding").then(function (r) {
          return r.ok ? r.json() : null;
        });
      })
      .then(applyBranding)
      .catch(function () { /* unbranded page */ });
  }

  // ---- Bot protection (CAPTCHA) ---------------------------------------------------------------------------------
  // Platform settings › Attack protection. The widget goes just above the button of each protected form; its
  // token is sent with the request (X-Captcha-Token, or the captchaToken field of the sign-in form).
  var CAPTCHA_SCRIPTS = {
    turnstile: "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=__soCaptchaReady",
    hcaptcha: "https://js.hcaptcha.com/1/api.js?render=explicit&onload=__soCaptchaReady",
    recaptcha: "https://www.google.com/recaptcha/api.js?render=explicit&onload=__soCaptchaReady",
  };
  var CAPTCHA_PATHS = [
    { action: "signUp", re: /\/api\/v1\/applications\/[^/]+\/signup$/ },
    { action: "passwordReset", re: /\/account\/password\/forgot$/ },
    { action: "magicLink", re: /\/account\/magic-link\/request$/ },
    { action: "oneTimeCodes", re: /\/auth\/otp\/request$/ },
  ];
  var captcha = { api: null, widgets: {}, tokens: {} };

  function captchaApi(provider) {
    return provider === "turnstile" ? window.turnstile : provider === "hcaptcha" ? window.hcaptcha : window.grecaptcha;
  }

  function placeBefore(target, action) {
    if (!target || !target.parentNode) return null;
    var box = document.createElement("div");
    box.className = "so-captcha";
    box.setAttribute("data-captcha-for", action);
    target.parentNode.insertBefore(box, target);
    return box;
  }

  function captchaSpots(cfg) {
    var path = location.pathname;
    var spots = [];
    function add(on, action, selector) {
      if (on) {
        var el = document.querySelector(selector);
        if (el) spots.push({ action: action, el: el });
      }
    }
    if (path === "/login.html") {
      add(cfg.signIn === "always" || params.get("error") === "captcha", "signIn", "#login-form button[type=submit]");
      add(cfg.oneTimeCodes, "oneTimeCodes", "#otp-send");
    } else if (path === "/account/signup.html") {
      add(cfg.signUp, "signUp", "#form button[type=submit]");
    } else if (path === "/account/forgot-password.html") {
      add(cfg.passwordReset, "passwordReset", "#submitBtn");
    } else if (path === "/account/magic-link.html") {
      add(cfg.magicLink, "magicLink", "#form button[type=submit]");
    }
    return spots;
  }

  function resetCaptcha(action) {
    captcha.tokens[action] = null;
    if (captcha.api && captcha.widgets[action] !== undefined) {
      try { captcha.api.reset(captcha.widgets[action]); } catch (e) { /* widget gone */ }
    }
  }

  function setupCaptcha() {
    fetch("/api/v1/attack-protection")
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (cfg) {
        if (!cfg || !CAPTCHA_SCRIPTS[cfg.provider] || !cfg.siteKey) return;
        var spots = captchaSpots(cfg);
        if (!spots.length) return;
        window.__soCaptchaReady = function () {
          captcha.api = captchaApi(cfg.provider);
          spots.forEach(function (spot) {
            var box = placeBefore(spot.el, spot.action);
            if (!box) return;
            captcha.widgets[spot.action] = captcha.api.render(box, {
              sitekey: cfg.siteKey,
              callback: function (token) { captcha.tokens[spot.action] = token; },
              "expired-callback": function () { captcha.tokens[spot.action] = null; },
            });
          });
        };
        var s = document.createElement("script");
        s.src = CAPTCHA_SCRIPTS[cfg.provider];
        s.async = true;
        s.defer = true;
        document.head.appendChild(s);

        // The sign-in form posts normally: carry the token as a field.
        var form = document.getElementById("login-form");
        if (form && spots.some(function (x) { return x.action === "signIn"; })) {
          form.addEventListener("submit", function () {
            var field = form.querySelector("input[name=captchaToken]") || document.createElement("input");
            field.type = "hidden";
            field.name = "captchaToken";
            field.value = captcha.tokens.signIn || "";
            form.appendChild(field);
          });
        }
      })
      .catch(function () { /* no bot protection */ });
  }

  // The other forms post with fetch: add the token header to protected requests, and use each token once.
  var nativeFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    var url = typeof input === "string" ? input : input && input.url;
    var method = (init && init.method) || (input && input.method) || "GET";
    if (url && String(method).toUpperCase() === "POST") {
      for (var i = 0; i < CAPTCHA_PATHS.length; i++) {
        var p = CAPTCHA_PATHS[i];
        var path = String(url).split("?")[0];
        if (p.re.test(path) && captcha.tokens[p.action]) {
          init = Object.assign({}, init || {});
          var headers = new Headers(init.headers || {});
          headers.set("X-Captcha-Token", captcha.tokens[p.action]);
          init.headers = headers;
          var action = p.action;
          return nativeFetch(input, init).finally(function () { resetCaptcha(action); });
        }
      }
    }
    return nativeFetch(input, init);
  };

  var style = document.createElement("style");
  style.textContent = [
    "html.so-branded body { background: var(--so-bg); color: var(--so-text); }",
    "html.so-branded button:not(.fed-btn):not(.so-plain) { background: var(--so-primary); color: var(--so-button-text); border-radius: var(--so-radius); }",
    "html.so-branded a { color: var(--so-primary); }",
    "html.so-branded input, html.so-branded select { background: var(--so-input-bg); color: var(--so-input-text); border-color: var(--so-input-border); border-radius: var(--so-radius); }",
    "html.so-branded .fed-btn { border-radius: var(--so-radius); }",
    ".so-captcha { margin: 0.25rem 0 0.75rem; min-height: 65px; }",
    ".so-logo { display: block; max-height: 48px; max-width: 200px; margin: 0 0 1rem; }",
    ".so-lang { margin: 2rem 0 1rem; font-size: 0.8rem; color: var(--so-muted, #6b7280); display: flex; gap: 0.5rem; align-items: center; }",
    ".so-lang label { margin: 0; display: inline; }",
    ".so-lang select { width: auto; padding: 0.25rem 0.5rem; margin: 0; font-size: 0.8rem; }",
    ".so-impersonation { position: sticky; top: 0; z-index: 50; margin: 0 0 1rem; padding: 0.6rem 1rem; background: #b45309; color: #fff; font-size: 0.85rem; text-align: center; }",
  ].join("\n");
  document.head.appendChild(style);

  /** An administrator signed in as this user: a banner says so on every page (account changes are refused). */
  function showImpersonation() {
    fetch("/api/v1/auth/impersonation/status", { credentials: "same-origin", headers: { Accept: "application/json" } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (s) {
        if (!s || !s.impersonated) return;
        var bar = document.createElement("div");
        bar.className = "so-impersonation";
        bar.setAttribute("role", "status");
        bar.textContent = "An administrator (" + s.by + ") is signed in as this user. Account changes are not available.";
        document.body.insertBefore(bar, document.body.firstChild);
      })
      .catch(function () {});
  }

  function start() {
    addPicker();
    applyLanguage();
    loadBranding();
    setupCaptcha();
    showImpersonation();
    // Text the pages add or change later (messages, two-step dialogs) is translated as it appears.
    new MutationObserver(function (mutations) {
      if (lang === "en") return;
      mutations.forEach(function (m) {
        if (m.type === "characterData") translateTextNode(m.target);
        else if (m.type === "attributes") translateElement(m.target);
        else m.addedNodes.forEach(translateTree);
      });
      translateTitle();
    }).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();

  window.SecureOneHosted = { translate: tr, language: function () { return lang; } };
})();
