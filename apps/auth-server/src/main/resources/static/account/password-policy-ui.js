(function (global) {
  function evaluate(password, policy) {
    const value = password || "";
    const minLength = policy.minLength || 12;
    const rules = [{ key: "minLength", label: "At least " + minLength + " characters", met: value.length >= minLength }];
    if (policy.requireUppercase) {
      rules.push({ key: "uppercase", label: "One uppercase letter (A–Z)", met: /[A-Z]/.test(value) });
    }
    if (policy.requireNumber) {
      rules.push({ key: "number", label: "One number (0–9)", met: /[0-9]/.test(value) });
    }
    if (policy.requireSymbol) {
      rules.push({ key: "symbol", label: "One symbol (!@#$… )", met: /[^a-zA-Z0-9]/.test(value) });
    }
    return rules;
  }

  function fromRequirements(policy) {
    if (policy.requirements && policy.requirements.length) {
      return policy.requirements.map(function (r) {
        return { key: r.key, label: r.label, met: false };
      });
    }
    return evaluate("", policy).map(function (r) {
      return { key: r.key, label: r.label, met: false };
    });
  }

  function renderChecklist(container, policy, password) {
    if (!container) return;
    const rules = evaluate(password || "", policy);
    container.innerHTML = "";
    const ul = document.createElement("ul");
    ul.style.cssText = "list-style:none;padding:0;margin:0 0 1rem;font-size:0.8rem;line-height:1.5";
    rules.forEach(function (rule) {
      const li = document.createElement("li");
      li.style.color = rule.met ? "#047857" : "#666";
      li.textContent = (rule.met ? "✓ " : "○ ") + rule.label;
      ul.appendChild(li);
    });
    container.appendChild(ul);
  }

  function meetsPolicy(password, policy) {
    return evaluate(password, policy).every(function (r) {
      return r.met;
    });
  }

  function loadPolicy(applicationId) {
    if (!applicationId) {
      return Promise.resolve({ minLength: 12, requireUppercase: false, requireNumber: false, requireSymbol: false });
    }
    return fetch("/api/v1/applications/" + encodeURIComponent(applicationId) + "/signup")
      .then(function (r) {
        return r.ok ? r.json() : {};
      })
      .then(function (opts) {
        return opts.passwordPolicy || { minLength: 12, requireUppercase: false, requireNumber: false, requireSymbol: false };
      })
      .catch(function () {
        return { minLength: 12, requireUppercase: false, requireNumber: false, requireSymbol: false };
      });
  }

  function bindPasswordField(input, checklistEl, policy) {
    function refresh() {
      renderChecklist(checklistEl, policy, input.value);
    }
    input.addEventListener("input", refresh);
    input.minLength = policy.minLength || 12;
    refresh();
  }

  global.SecureOnePasswordPolicy = {
    evaluate: evaluate,
    fromRequirements: fromRequirements,
    renderChecklist: renderChecklist,
    meetsPolicy: meetsPolicy,
    loadPolicy: loadPolicy,
    bindPasswordField: bindPasswordField,
  };
})(window);
