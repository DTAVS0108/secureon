/*
 * Two-step verification for the hosted pages (sign-in second step, setup during sign-in, account page).
 * Plain JavaScript, no build step. Uses /api/v1/auth/mfa/* (during sign-in) and /api/v1/account/2fa/* (signed in).
 */
(function () {
  "use strict";

  var LABELS = {
    passkey: "Passkey",
    security_key: "Security key",
    totp: "Authenticator app",
    sms: "Text message",
    email: "Email code",
    recovery: "Backup code",
  };
  var HINTS = {
    passkey: "Use your fingerprint, face or device PIN. The safest option.",
    security_key: "A USB or NFC key such as a YubiKey.",
    totp: "A 6-digit code from Google Authenticator, Microsoft Authenticator, 1Password…",
    sms: "A code sent to your phone by text message. Less safe than the options above.",
    email: "A code sent to your email. The least safe option.",
    recovery: "One of the backup codes you saved when you set this up.",
  };

  // ---- small helpers --------------------------------------------------------------------------------------

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (k === "text") node.textContent = attrs[k];
      else if (k === "html") node.innerHTML = attrs[k];
      else if (k.indexOf("on") === 0) node.addEventListener(k.substring(2), attrs[k]);
      else if (attrs[k] !== undefined && attrs[k] !== null && attrs[k] !== false) node.setAttribute(k, attrs[k]);
    });
    (children || []).forEach(function (c) {
      if (c) node.appendChild(typeof c === "string" ? document.createTextNode(c) : c);
    });
    return node;
  }

  function call(method, path, body) {
    return fetch(path, {
      method: method,
      credentials: "same-origin",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    }).then(function (res) {
      if (res.status === 204) return { ok: true, status: 204, body: null };
      return res.json().catch(function () { return {}; }).then(function (b) {
        return { ok: res.ok, status: res.status, body: b || {} };
      });
    });
  }

  function errorText(r, fallback) {
    return (r.body && (r.body.detail || r.body.message)) || fallback || "That didn't work. Try again.";
  }

  function b64ToBytes(s) {
    return Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), function (c) { return c.charCodeAt(0); });
  }

  function bytesToB64(buf) {
    var bin = "";
    new Uint8Array(buf).forEach(function (b) { bin += String.fromCharCode(b); });
    return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  function unwrap(publicKey) {
    var pk = typeof publicKey === "string" ? JSON.parse(publicKey) : publicKey;
    return pk.publicKey ? pk.publicKey : pk;
  }

  /** navigator.credentials.get for options from the server; returns the JSON the server verifies. */
  function webauthnGet(publicKey) {
    var pk = unwrap(publicKey);
    pk.challenge = b64ToBytes(pk.challenge);
    if (pk.allowCredentials) {
      pk.allowCredentials = pk.allowCredentials.map(function (c) { return Object.assign({}, c, { id: b64ToBytes(c.id) }); });
    }
    return navigator.credentials.get({ publicKey: pk }).then(function (a) {
      return JSON.stringify({
        id: a.id, rawId: bytesToB64(a.rawId), type: a.type,
        response: {
          clientDataJSON: bytesToB64(a.response.clientDataJSON),
          authenticatorData: bytesToB64(a.response.authenticatorData),
          signature: bytesToB64(a.response.signature),
          userHandle: a.response.userHandle ? bytesToB64(a.response.userHandle) : null,
        },
        clientExtensionResults: a.getClientExtensionResults ? a.getClientExtensionResults() : {},
      });
    });
  }

  /** navigator.credentials.create for registration options from the server. */
  function webauthnCreate(publicKey) {
    var pk = unwrap(publicKey);
    pk.challenge = b64ToBytes(pk.challenge);
    pk.user.id = b64ToBytes(pk.user.id);
    if (pk.excludeCredentials) {
      pk.excludeCredentials = pk.excludeCredentials.map(function (c) { return Object.assign({}, c, { id: b64ToBytes(c.id) }); });
    }
    return navigator.credentials.create({ publicKey: pk }).then(function (c) {
      return JSON.stringify({
        id: c.id, rawId: bytesToB64(c.rawId), type: c.type,
        response: {
          attestationObject: bytesToB64(c.response.attestationObject),
          clientDataJSON: bytesToB64(c.response.clientDataJSON),
        },
        clientExtensionResults: c.getClientExtensionResults ? c.getClientExtensionResults() : {},
      });
    });
  }

  function message(box, text, ok) {
    box.textContent = text;
    box.className = "ts-msg " + (ok ? "ts-ok" : "ts-err");
    box.hidden = !text;
  }

  function codeInput(id, placeholder) {
    return el("input", { id: id, inputmode: "numeric", autocomplete: "one-time-code", maxlength: "10",
      placeholder: placeholder || "6-digit code", "data-testid": id });
  }

  function formatDate(iso) {
    try { return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }); }
    catch (e) { return iso; }
  }

  function backupCodesView(codes, onDone) {
    var list = el("ul", { class: "ts-codes", "data-testid": "backup-codes" },
      codes.map(function (c) { return el("li", { text: c }); }));
    var copy = el("button", { type: "button", class: "ts-link", text: "Copy codes", onclick: function () {
      if (navigator.clipboard) navigator.clipboard.writeText(codes.join("\n"));
      copy.textContent = "Copied";
    } });
    return el("div", { class: "ts-box" }, [
      el("strong", { text: "Save your backup codes" }),
      el("p", { class: "ts-muted", text: "Each code works once, if you can't use your other options. Keep them somewhere safe; they won't be shown again." }),
      list,
      copy,
      el("button", { type: "button", class: "ts-primary", "data-testid": "backup-codes-saved", text: "I saved them, continue", onclick: onDone }),
    ]);
  }

  // ---- Sign-in: second step and setup -----------------------------------------------------------------------

  /**
   * Renders the step after the password (or other first step) into {@code container}. {@code onSignedIn} is
   * called once the user is signed in.
   */
  function renderSignInStep(container, opts) {
    var challengeId = opts.challengeId;
    var base = "/api/v1/auth/mfa";
    container.innerHTML = "";
    var msg = el("p", { class: "ts-msg", hidden: true, role: "alert" });
    container.appendChild(el("p", { class: "ts-muted", text: "Loading…" }));
    call("GET", base + "/challenge/" + encodeURIComponent(challengeId)).then(function (r) {
      container.innerHTML = "";
      if (!r.ok) {
        container.appendChild(el("p", { class: "ts-msg ts-err", text: "This sign-in expired. Sign in again." }));
        return;
      }
      if (r.body.stage === "setup") renderSetup(r.body);
      else renderVerify(r.body);
    });

    function finished(r) {
      if (r.ok) {
        var codes = r.body && r.body.recoveryCodes ? r.body.recoveryCodes : [];
        if (codes.length) {
          container.innerHTML = "";
          container.appendChild(backupCodesView(codes, function () { opts.onSignedIn(); }));
        } else {
          opts.onSignedIn();
        }
        return true;
      }
      message(msg, errorText(r));
      if (r.status === 410) setTimeout(function () { location.href = "/login.html"; }, 1500);
      return false;
    }

    // -- verify ------------------------------------------------------------------------------------------------
    function renderVerify(ch) {
      var options = ch.options || [];
      var remember = el("input", { type: "checkbox", id: "ts-remember", "data-testid": "remember-device" });
      var rememberRow = ch.rememberDeviceDays > 0
        ? el("label", { class: "ts-check" }, [remember, " Don't ask again on this device for " + ch.rememberDeviceDays + " days"])
        : null;
      var body = el("div", { class: "ts-body" });
      container.appendChild(el("div", { class: "ts-box", "data-testid": "two-step-verify" }, [
        el("strong", { text: "Verify it's you" }),
        body,
        rememberRow,
        msg,
      ]));
      var choices = options.slice();
      if (ch.recovery) choices.push({ id: "recovery", type: "recovery", label: LABELS.recovery });
      if (!choices.length) {
        body.appendChild(el("p", { text: "No option is available. Ask an administrator to reset your two-step verification." }));
        return;
      }
      var current = choices[0];
      show(current);

      function show(option) {
        current = option;
        body.innerHTML = "";
        message(msg, "");
        body.appendChild(form(option));
        // Can't use this option (lost phone, no key at hand): list every option this user has enabled.
        // Only while the application's "2FA: Try another way" feature flag is on.
        if (ch.tryAnotherWay !== false && choices.length > 1) {
          body.appendChild(el("button", { type: "button", class: "ts-link ts-another", "data-testid": "try-another-way",
            text: option.type === "recovery" ? "Try another way" : "Can't use your " + LABELS[option.type].toLowerCase() + "? Try another way",
            onclick: chooseAnother }));
        }
      }

      function chooseAnother() {
        body.innerHTML = "";
        message(msg, "");
        body.appendChild(el("p", { class: "ts-muted", text: "Choose how to verify it's you:" }));
        body.appendChild(el("div", { "data-testid": "two-step-options" }, choices.map(function (o) {
          return el("button", { type: "button", class: "ts-option", "data-testid": "option-" + o.type, onclick: function () { show(o); } }, [
            el("span", { class: "ts-option-title", text: describe(o) + (o === current ? " · current" : "") }),
            HINTS[o.type] ? el("span", { class: "ts-muted", text: HINTS[o.type] }) : null,
          ]);
        })));
        body.appendChild(el("p", { class: "ts-muted", text: "None of these work? Ask an administrator to reset your two-step verification." }));
        body.appendChild(el("button", { type: "button", class: "ts-link", text: "← Back", onclick: function () { show(current); } }));
      }

      function describe(o) {
        if (o.type === "sms") return "Text a code to " + (o.masked || "your phone");
        if (o.type === "email") return "Email a code to " + (o.masked || "your email");
        if (o.type === "totp") return "Code from your authenticator app";
        if (o.type === "passkey") return "Use your passkey";
        if (o.type === "security_key") return "Use your security key";
        return "Use a backup code";
      }

      function form(o) {
        var rememberValue = function () { return !!remember.checked; };
        if (o.type === "passkey" || o.type === "security_key") {
          return el("div", {}, [
            el("p", { class: "ts-muted", text: o.type === "passkey" ? "Confirm with your fingerprint, face or device PIN." : "Insert or tap your security key." }),
            el("button", { type: "button", class: "ts-primary", "data-testid": "use-webauthn", text: describe(o), onclick: function () {
              call("POST", base + "/webauthn/options", { mfaChallengeId: challengeId }).then(function (r) {
                if (!r.ok) return message(msg, errorText(r));
                return webauthnGet(r.body.publicKey).then(function (credential) {
                  return call("POST", base + "/webauthn/verify", { mfaChallengeId: challengeId, requestId: r.body.requestId,
                    credential: credential, rememberDevice: rememberValue() });
                }).then(finished);
              }).catch(function () { message(msg, "The passkey or security key was not used. Try again or choose another way."); });
            } }),
          ]);
        }
        if (o.type === "sms" || o.type === "email") {
          var input = codeInput("ts-sent-code");
          var verify = el("button", { type: "button", class: "ts-primary", "data-testid": "verify-sent-code", text: "Verify", disabled: true,
            onclick: function () {
              call("POST", base + "/code/verify", { mfaChallengeId: challengeId, code: input.value.trim(), rememberDevice: rememberValue() }).then(finished);
            } });
          return el("div", {}, [
            el("button", { type: "button", "data-testid": "send-code", text: describe(o), onclick: function (e) {
              call("POST", base + "/send", { mfaChallengeId: challengeId, factorId: o.id }).then(function (r) {
                if (!r.ok) return message(msg, errorText(r));
                message(msg, "Code sent to " + (r.body.sentTo || "you") + ".", true);
                verify.disabled = false;
                e.target.textContent = "Send a new code";
              });
            } }),
            el("label", { for: "ts-sent-code", text: "Code" }), input, verify,
          ]);
        }
        if (o.type === "recovery") {
          var rc = el("input", { id: "ts-recovery", autocomplete: "off", "data-testid": "recovery-code" });
          return el("div", {}, [
            el("label", { for: "ts-recovery", text: "Backup code" }), rc,
            el("button", { type: "button", class: "ts-primary", "data-testid": "verify-recovery", text: "Verify", onclick: function () {
              call("POST", base + "/recovery/verify", { mfaChallengeId: challengeId, code: rc.value.trim() }).then(finished);
            } }),
          ]);
        }
        var code = codeInput("mfa-code", "6-digit code");
        var submit = function () {
          call("POST", base + "/totp/verify", { mfaChallengeId: challengeId, code: code.value.trim(), rememberDevice: rememberValue() }).then(finished);
        };
        code.addEventListener("keydown", function (e) { if (e.key === "Enter") submit(); });
        return el("div", {}, [
          el("label", { for: "mfa-code", text: "Code from your authenticator app" }), code,
          el("button", { type: "button", class: "ts-primary", id: "mfa-submit", "data-testid": "verify-totp", text: "Verify and continue", onclick: submit }),
        ]);
      }
    }

    // -- setup -------------------------------------------------------------------------------------------------
    function renderSetup(ch) {
      var heading = ch.required ? "Set up two-step verification" : "Protect your account";
      var intro = ch.required
        ? (ch.canSkip
          ? "This application requires a second step when you sign in. Set it up by " + formatDate(ch.deadline) + "."
          : "This application requires a second step when you sign in. Set it up now to continue.")
        : "Add a second step so a stolen password alone can't open your account.";
      var body = el("div", { class: "ts-body" });
      var box = el("div", { class: "ts-box", "data-testid": "two-step-setup" }, [el("strong", { text: heading }), el("p", { text: intro }), body, msg]);
      container.appendChild(box);
      choose();

      function choose() {
        body.innerHTML = "";
        message(msg, "");
        (ch.offered || []).forEach(function (type, i) {
          body.appendChild(el("button", { type: "button", class: "ts-option", "data-testid": "setup-" + type, onclick: function () { start(type); } }, [
            el("span", { class: "ts-option-title", text: LABELS[type] + (i === 0 && (type === "passkey" || type === "security_key") ? " · Recommended" : "") }),
            el("span", { class: "ts-muted", text: HINTS[type] || "" }),
          ]));
        });
        if (ch.canSkip) {
          var later = ch.required
            ? "Set up later" + (ch.skipsLeft ? " (" + ch.skipsLeft + (ch.skipsLeft === 1 ? " time" : " times") + " left)" : "")
            : "Remind me later";
          var row = el("div", { class: "ts-row" }, [
            el("button", { type: "button", class: "ts-link", "data-testid": "skip-setup", text: later, onclick: function () { skip(false); } }),
            ch.allowDontAskAgain ? el("button", { type: "button", class: "ts-link", "data-testid": "dont-ask-again", text: "Don't ask again", onclick: function () { skip(true); } }) : null,
          ]);
          body.appendChild(row);
        }
      }

      function skip(dontAskAgain) {
        call("POST", base + "/skip", { mfaChallengeId: challengeId, dontAskAgain: dontAskAgain }).then(finished);
      }

      function back() {
        return el("button", { type: "button", class: "ts-link", text: "← Other options", onclick: choose });
      }

      function start(type) {
        body.innerHTML = "";
        message(msg, "");
        if (type === "passkey" || type === "security_key") {
          body.appendChild(el("p", { text: type === "passkey" ? "Your browser will ask to create a passkey on this device." : "Insert or tap your security key when your browser asks." }));
          call("POST", base + "/setup/webauthn/options", { mfaChallengeId: challengeId, kind: type }).then(function (r) {
            if (!r.ok) return message(msg, errorText(r));
            return webauthnCreate(r.body.publicKey).then(function (credential) {
              return call("POST", base + "/setup/webauthn/verify", { mfaChallengeId: challengeId, requestId: r.body.requestId, credential: credential });
            }).then(finished);
          }).catch(function () { message(msg, "The passkey or security key was not created. Try again or choose another option."); });
          body.appendChild(back());
          return;
        }
        if (type === "totp") {
          call("POST", base + "/setup/totp/start", { mfaChallengeId: challengeId }).then(function (r) {
            if (!r.ok) return message(msg, errorText(r));
            var code = codeInput("ts-setup-totp");
            body.appendChild(el("p", { text: "Scan this with your authenticator app, then enter the code it shows." }));
            body.appendChild(el("img", { src: r.body.qrCodeDataUrl, alt: "QR code", class: "ts-qr" }));
            body.appendChild(el("p", { class: "ts-muted" }, ["Or enter this key: ", el("code", { "data-testid": "setup-secret", text: r.body.secret })]));
            body.appendChild(code);
            body.appendChild(el("button", { type: "button", class: "ts-primary", "data-testid": "setup-totp-confirm", text: "Confirm", onclick: function () {
              call("POST", base + "/setup/totp/confirm", { mfaChallengeId: challengeId, code: code.value.trim() }).then(finished);
            } }));
            body.appendChild(back());
          });
          return;
        }
        // sms / email
        var phone = el("input", { id: "ts-phone", type: "tel", autocomplete: "tel", placeholder: "+1 415 555 0100", "data-testid": "setup-phone" });
        var code = codeInput("ts-setup-code");
        var confirm = el("button", { type: "button", class: "ts-primary", "data-testid": "setup-code-confirm", text: "Confirm", disabled: true, onclick: function () {
          call("POST", base + "/setup/code/confirm", { mfaChallengeId: challengeId, code: code.value.trim() }).then(finished);
        } });
        var send = el("button", { type: "button", "data-testid": "setup-code-send", text: type === "sms" ? "Text me a code" : "Email me a code", onclick: function () {
          call("POST", base + "/setup/code/send", { mfaChallengeId: challengeId, method: type, phone: phone.value.trim() }).then(function (r) {
            if (!r.ok) return message(msg, errorText(r));
            message(msg, "Code sent to " + r.body.sentTo + ".", true);
            confirm.disabled = false;
          });
        } });
        if (type === "sms") body.appendChild(el("label", { for: "ts-phone", text: "Mobile number" })), body.appendChild(phone);
        body.appendChild(send);
        body.appendChild(el("label", { for: "ts-setup-code", text: "Code" }));
        body.appendChild(code);
        body.appendChild(confirm);
        body.appendChild(back());
      }
    }
  }

  // ---- Account page --------------------------------------------------------------------------------------

  function renderAccount(container, opts) {
    var app = opts.applicationId ? "?applicationId=" + encodeURIComponent(opts.applicationId) : "";
    var base = "/api/v1/account/2fa";
    var msg = el("p", { class: "ts-msg", hidden: true, role: "alert" });

    function load() {
      call("GET", base + app).then(function (r) {
        container.innerHTML = "";
        if (r.status === 401) {
          container.appendChild(el("p", {}, ["Sign in to manage two-step verification. ", el("a", { href: "/login.html" + app, text: "Sign in" })]));
          return;
        }
        if (!r.ok) return container.appendChild(el("p", { class: "ts-msg ts-err", text: errorText(r) }));
        render(r.body);
      });
    }

    /** Runs a sensitive change; asks the user to verify first when the server says so, then retries once. */
    function sensitive(run) {
      return run().then(function (r) {
        if (r.status === 403 && r.body && r.body.freshStepRequired) {
          return verifyDialog().then(function (ok) { return ok ? run() : r; });
        }
        return r;
      });
    }

    function verifyDialog() {
      return new Promise(function (resolve) {
        call("GET", base + app).then(function (r) {
          var methods = (r.body && r.body.methods) || [];
          var dlg = el("div", { class: "ts-dialog", role: "dialog", "data-testid": "verify-dialog" });
          var dmsg = el("p", { class: "ts-msg", hidden: true });
          var bodyEl = el("div");
          var close = function (ok) { dlg.remove(); resolve(ok); };
          var done = function (res) { if (res.ok) close(true); else message(dmsg, errorText(res)); };
          dlg.appendChild(el("div", { class: "ts-box" }, [
            el("strong", { text: "Verify it's you" }),
            el("p", { class: "ts-muted", text: "This change needs a quick check with one of your sign-in options." }),
            bodyEl, dmsg,
            el("button", { type: "button", class: "ts-link", text: "Cancel", onclick: function () { close(false); } }),
          ]));
          methods.forEach(function (m) {
            if (m.type === "totp") {
              var input = codeInput("ts-verify-totp");
              bodyEl.appendChild(el("div", {}, [el("label", { text: "Authenticator code" }), input,
                el("button", { type: "button", class: "ts-primary", "data-testid": "verify-dialog-totp", text: "Verify", onclick: function () {
                  call("POST", base + "/verify", { method: "totp", code: input.value.trim() }).then(done);
                } })]));
            } else if (m.type === "passkey" || m.type === "security_key") {
              bodyEl.appendChild(el("button", { type: "button", "data-testid": "verify-dialog-webauthn", text: "Use your " + LABELS[m.type].toLowerCase(), onclick: function () {
                call("POST", base + "/verify/webauthn/options").then(function (o) {
                  return webauthnGet(o.body.publicKey).then(function (credential) {
                    return call("POST", base + "/verify", { method: "webauthn", requestId: o.body.requestId, credential: credential });
                  });
                }).then(done).catch(function () { message(dmsg, "Not verified. Try again."); });
              } }));
            } else if (m.type === "sms" || m.type === "email") {
              var c = codeInput("ts-verify-code-" + m.id);
              bodyEl.appendChild(el("div", {}, [
                el("button", { type: "button", text: (m.type === "sms" ? "Text" : "Email") + " a code to " + (m.masked || ""), onclick: function () {
                  call("POST", base + "/verify/send" + app, { factorId: m.id }).then(function (s) { message(dmsg, s.ok ? "Code sent." : errorText(s), s.ok); });
                } }), c,
                el("button", { type: "button", text: "Verify", onclick: function () {
                  call("POST", base + "/verify", { method: "code", code: c.value.trim() }).then(done);
                } })]));
            }
          });
          var rc = el("input", { placeholder: "Backup code", autocomplete: "off" });
          bodyEl.appendChild(el("details", {}, [el("summary", { text: "Use a backup code" }), rc,
            el("button", { type: "button", text: "Verify", onclick: function () {
              call("POST", base + "/verify", { method: "recovery", code: rc.value.trim() }).then(done);
            } })]));
          document.body.appendChild(dlg);
        });
      });
    }

    function render(st) {
      var statusText = {
        enabled: "On",
        off: "Off",
        in_grace: "Required by " + (st.deadline ? formatDate(st.deadline) : "your administrator"),
        overdue: "Required: set it up now",
        not_set_up: "Required by your administrator",
        exempt: "Not required for you until " + (st.exemptUntil ? formatDate(st.exemptUntil) : ""),
      }[st.status] || st.status;
      container.appendChild(el("p", { "data-testid": "two-step-status" }, [el("strong", { text: "Two-step verification: " }), statusText]));
      container.appendChild(msg);

      // Options
      var list = el("ul", { class: "ts-list", "data-testid": "two-step-methods" });
      (st.methods || []).forEach(function (m) {
        list.appendChild(el("li", { "data-testid": "method-" + m.type }, [
          el("span", {}, [el("strong", { text: m.label }), m.masked ? " · " + m.masked : "", m.isDefault ? el("em", { text: " · asked first" }) : null,
            el("span", { class: "ts-muted", text: " · added " + formatDate(m.createdAt) + (m.lastUsedAt ? ", last used " + formatDate(m.lastUsedAt) : "") })]),
          el("span", { class: "ts-actions" }, [
            m.isDefault ? null : el("button", { type: "button", class: "ts-link", text: "Ask first", onclick: function () {
              call("POST", base + "/methods/" + m.id + "/default").then(load);
            } }),
            el("button", { type: "button", class: "ts-link", "data-testid": "remove-" + m.type, text: "Remove", onclick: function () {
              if (!confirm("Remove " + m.label + "?")) return;
              sensitive(function () { return call("DELETE", base + "/methods/" + m.id + app); }).then(function (r) {
                if (!r.ok) message(msg, errorText(r)); else load();
              });
            } }),
          ]),
        ]));
      });
      if (!(st.methods || []).length) list.appendChild(el("li", { class: "ts-muted", text: "No options yet." }));
      container.appendChild(el("h3", { text: "Ways to verify it's you" }));
      container.appendChild(list);

      // Add
      var have = (st.methods || []).map(function (m) { return m.type; });
      var addRow = el("div", { class: "ts-row", "data-testid": "add-methods" });
      (st.offeredMethods || st.allowedMethods || []).forEach(function (type) {
        if (type === "totp" && have.indexOf("totp") >= 0) return;
        addRow.appendChild(el("button", { type: "button", "data-testid": "add-" + type, text: "Add " + LABELS[type].toLowerCase(), onclick: function () { add(type); } }));
      });
      container.appendChild(addRow);
      var addBox = el("div", { class: "ts-box", hidden: true });
      container.appendChild(addBox);

      // Backup codes
      container.appendChild(el("h3", { text: "Backup codes" }));
      container.appendChild(el("p", { "data-testid": "backup-remaining", class: st.backupCodesRemaining <= 3 && (st.methods || []).length ? "ts-msg ts-err" : "" },
        [String(st.backupCodesRemaining) + " left" + (st.backupCodesRemaining <= 3 && (st.methods || []).length ? ": get new codes soon." : ".")]));
      if ((st.methods || []).length) {
        container.appendChild(el("button", { type: "button", "data-testid": "new-backup-codes", text: "Get new backup codes", onclick: function () {
          sensitive(function () { return call("POST", base + "/backup-codes" + app); }).then(function (r) {
            if (!r.ok) return message(msg, errorText(r));
            addBox.hidden = false;
            addBox.innerHTML = "";
            addBox.appendChild(backupCodesView(r.body.recoveryCodes, function () { load(); }));
          });
        } }));
      }

      // Devices
      container.appendChild(el("h3", { text: "Remembered devices" }));
      var devices = el("ul", { class: "ts-list", "data-testid": "devices" });
      (st.devices || []).forEach(function (d) {
        // A device is remembered in one application; elsewhere the second step is still asked.
        var where = d.applicationId && opts.applicationId && d.applicationId !== opts.applicationId ? " · another application" : "";
        devices.appendChild(el("li", {}, [
          el("span", { text: (d.label || "Browser") + where + " · until " + formatDate(d.expiresAt) }),
          el("button", { type: "button", class: "ts-link", text: "Forget", onclick: function () {
            call("DELETE", base + "/devices/" + d.id).then(load);
          } }),
        ]));
      });
      if (!(st.devices || []).length) devices.appendChild(el("li", { class: "ts-muted", text: "None. Devices you tick “don't ask again” on appear here (each counts for the application you ticked it in)." }));
      container.appendChild(devices);
      if ((st.devices || []).length) {
        container.appendChild(el("button", { type: "button", text: "Forget all devices", onclick: function () { call("DELETE", base + "/devices").then(load); } }));
      }

      if (st.mode !== "required" && (st.methods || []).length) {
        container.appendChild(el("h3", { text: "Turn off" }));
        container.appendChild(el("button", { type: "button", "data-testid": "turn-off", text: "Turn off two-step verification", onclick: function () {
          if (!confirm("Turn off two-step verification? Your options and backup codes are removed.")) return;
          sensitive(function () { return call("POST", base + "/off" + app); }).then(function (r) { if (!r.ok) message(msg, errorText(r)); else load(); });
        } }));
      }

      function added(r) {
        if (!r.ok) return message(msg, errorText(r));
        var codes = (r.body && r.body.recoveryCodes) || [];
        if (codes.length) {
          addBox.hidden = false;
          addBox.innerHTML = "";
          addBox.appendChild(backupCodesView(codes, load));
        } else {
          load();
        }
      }

      function add(type) {
        addBox.hidden = false;
        addBox.innerHTML = "";
        message(msg, "");
        if (type === "passkey" || type === "security_key") {
          sensitive(function () { return call("POST", base + "/methods/webauthn/options" + app, { kind: type }); }).then(function (r) {
            if (!r.ok) return message(msg, errorText(r));
            return webauthnCreate(r.body.publicKey).then(function (credential) {
              return call("POST", base + "/methods/webauthn/verify" + app, { requestId: r.body.requestId, credential: credential });
            }).then(added);
          }).catch(function () { message(msg, "Not added. Try again."); });
          return;
        }
        if (type === "totp") {
          sensitive(function () { return call("POST", base + "/methods/totp/start" + app); }).then(function (r) {
            if (!r.ok) return message(msg, errorText(r));
            var code = codeInput("ts-add-totp");
            addBox.appendChild(el("p", { text: "Scan this with your authenticator app, then enter the code." }));
            addBox.appendChild(el("img", { src: r.body.qrCodeDataUrl, alt: "QR code", class: "ts-qr" }));
            addBox.appendChild(el("p", { class: "ts-muted" }, ["Key: ", el("code", { "data-testid": "add-totp-secret", text: r.body.secret })]));
            addBox.appendChild(code);
            addBox.appendChild(el("button", { type: "button", class: "ts-primary", "data-testid": "add-totp-confirm", text: "Confirm", onclick: function () {
              call("POST", base + "/methods/totp/confirm" + app, { factorId: r.body.factorId, code: code.value.trim() }).then(added);
            } }));
          });
          return;
        }
        var phone = el("input", { type: "tel", placeholder: "+1 415 555 0100", "data-testid": "add-phone" });
        var code = codeInput("ts-add-code");
        var confirmBtn = el("button", { type: "button", class: "ts-primary", "data-testid": "add-code-confirm", text: "Confirm", disabled: true, onclick: function () {
          call("POST", base + "/methods/code/confirm" + app, { code: code.value.trim() }).then(added);
        } });
        if (type === "sms") addBox.appendChild(phone);
        addBox.appendChild(el("button", { type: "button", "data-testid": "add-code-send", text: type === "sms" ? "Text me a code" : "Email me a code", onclick: function () {
          sensitive(function () { return call("POST", base + "/methods/code/send" + app, { method: type, phone: phone.value.trim() }); }).then(function (r) {
            if (!r.ok) return message(msg, errorText(r));
            message(msg, "Code sent to " + r.body.sentTo + ".", true);
            confirmBtn.disabled = false;
          });
        } }));
        addBox.appendChild(code);
        addBox.appendChild(confirmBtn);
      }
    }

    load();
  }

  // Styles shared by the hosted pages (they stay plain otherwise).
  var css = ".ts-box{border:1px solid #e5e7eb;border-radius:12px;padding:1rem;margin:.75rem 0;background:#fff}"
    + ".ts-muted{color:#6b7280;font-size:.85rem}.ts-msg{margin:.5rem 0;padding:.5rem .75rem;border-radius:8px;font-size:.875rem}"
    + ".ts-ok{background:#ecfdf5;color:#065f46}.ts-err{background:#fef2f2;color:#991b1b}"
    + ".ts-option{display:flex;flex-direction:column;align-items:flex-start;gap:.15rem;width:100%;text-align:left;margin:.35rem 0;padding:.6rem .75rem;border:1px solid #d1d5db;border-radius:10px;background:#fff;color:#111;cursor:pointer}"
    + ".ts-option-title{font-weight:600}.ts-primary{background:#4f46e5!important;color:#fff!important;border-color:transparent!important}"
    + ".ts-link{background:none!important;border:none!important;color:#4f46e5!important;padding:0 .25rem!important;cursor:pointer;width:auto!important;font-weight:500}"
    + ".ts-row{display:flex;gap:.75rem;flex-wrap:wrap;margin-top:.5rem}.ts-codes{columns:2;font-family:monospace;padding-left:1.2rem}"
    + ".ts-qr{width:10rem;height:10rem;border:1px solid #e5e7eb;border-radius:8px}.ts-check{display:flex;gap:.4rem;align-items:center;font-size:.875rem;margin-top:.5rem}"
    + ".ts-list{list-style:none;padding:0}.ts-list li{display:flex;justify-content:space-between;gap:.5rem;padding:.5rem 0;border-bottom:1px solid #f3f4f6;flex-wrap:wrap}"
    + ".ts-dialog{position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;padding:1rem;z-index:50}"
    + ".ts-dialog .ts-box{max-width:26rem;width:100%}.ts-another{display:block;margin-top:.75rem!important;font-size:.875rem;text-align:left}";
  var style = document.createElement("style");
  style.textContent = css;
  document.head.appendChild(style);

  window.SecureOneTwoStep = { renderSignInStep: renderSignInStep, renderAccount: renderAccount };
})();
