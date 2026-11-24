---
title: Sign-in themes
summary: Your own design for the hosted sign-in, sign-up, password reset and account pages, without rebuilding the image
order: 11
audience: public
---

# Sign-in themes

[User guide](index.md) › Sign-in themes

A **sign-in theme** changes how SecureOne's [end-user pages](end-user-pages.md) look: sign-in, sign-up, forgot and
reset password, set password, magic link, connect a device, account security, privacy, the consent screen, and the
short messages SecureOne shows itself ("This app cannot sign you in right now", "Email verified", an expired sign-in
link, SAML "Signing in…").

Themes are folders on the server. You add or edit them while SecureOne runs, and every application picks
one. You never rebuild the image. A theme holds only what it changes, and everything else comes from SecureOne's
built-in pages. Each application's **Appearance** logo, font size and default language still apply. Its colours
apply too, unless the theme has its own stylesheet: the theme then decides, and the colours are available to it as
[CSS variables](#styles-themecss).

You can go as far as you need:

| Level | Files | What changes | When SecureOne is upgraded |
|---|---|---|---|
| 1. Styles | `theme.css`, `assets/` | Colours, fonts, layout, background, your logo | Keeps working |
| 2. Wording | `messages/<language>.json` | Any text on the pages, in any language; extra languages | Keeps working (new texts show in SecureOne's wording until you add them) |
| 3. Whole pages | `pages/…`, `templates/…` | The page's HTML: what is shown where | Compare your copies with the new built-in pages ([out-of-date check](#when-secureone-is-upgraded)) |

Most designs only need levels 1 and 2.

## Your first theme (Docker)

1. Copy the starter theme out of the image and name it after your theme:

   ```bash
   mkdir -p themes
   docker cp secureone:/opt/secureone/theme-example ./themes/acme
   ```

2. Mount the folder (read-only is enough) and start SecureOne again:

   ```bash
   docker run … -v "$PWD/themes:/opt/secureone/themes:ro" dmtavs0108/secureone:0.1.1
   ```

   In Docker Compose, add `- ./themes:/opt/secureone/themes:ro` to the `secureone` service's `volumes`.

3. In the console, open **Platform settings → Appearance**. **Sign-in themes** lists `acme`. Use **Preview** to see it.

4. Open the application, then **Settings → Appearance → Sign-in page theme**, and choose `acme`. Its sign-in pages use
   the theme from the next page load.

5. Edit `themes/acme/theme.css` or `themes/acme/messages/en.json`. In production the theme is read once, so press
   **Reload themes** (Platform settings → Appearance) after a change. With `SECUREONE_THEME_CACHE=false`, changes show
   on the next page load.

To make a theme the default for every application that does not choose one, set `SECUREONE_THEME=acme`.

## What is in a theme folder

```
themes/
  acme/
    theme.json            name of the parent theme, the SecureOne version, a description
    theme.css             added to every page
    messages/
      en.json             your wording, by language
      hi.json
    assets/
      logo.svg            images, fonts and scripts: /themes/acme/assets/…
      favicon.svg         becomes the page icon
    pages/                optional: your own copy of a page
      login.html
      account/signup.html
    templates/            optional: the layout of the pages SecureOne builds itself
      page.html
```

The folder name is the theme's name: lowercase letters, digits, `-` and `_` (up to 40). `base` is reserved for
SecureOne's built-in pages.

### theme.json

```json
{
  "parent": "base",
  "secureone": "0.1.1",
  "description": "Acme customer sign-in",
  "languages": [{ "code": "ta", "name": "தமிழ்" }]
}
```

| Field | Meaning |
|---|---|
| `parent` | The theme this one builds on. Anything this theme does not have comes from it. `base` (the default) is SecureOne's own pages. A brand theme can build on a company theme: `"parent": "corp"`. |
| `secureone` | The SecureOne version you made the theme for, or copied its pages from. Used to warn when SecureOne's own pages change ([below](#when-secureone-is-upgraded)). |
| `description` | Shown in the console. |
| `languages` | Extra languages for the page's language picker (two- or three-letter code and the name people see). Add their texts in `messages/<code>.json`. |

All fields are optional. Without `theme.json` the theme builds on `base`.

## Styles: theme.css

`theme.css` is added to every page after the page's own styles, so your rules win. With a parent theme, the
parent's `theme.css` comes first and yours after it.

When a theme (or its parent) has a `theme.css`, SecureOne no longer paints the application's **Appearance** colours
onto the page's buttons, links and fields itself: your stylesheet decides. Use the colour variables below where
each application's own colours should show.

- **Hosted pages** (sign-in, sign-up, reset, account security…) put their content straight into `<body>`: style
  `body` as your card or container.
- **Pages SecureOne builds itself** (consent, error and SAML pages) have `<body class="so-page so-page-<name>">`
  with a `<main><div class="card">…</div></main>` inside.
- Message boxes use `.msg` with `.ok` or `.err`, and secondary buttons use `.secondary`.
- The colours an application sets under **Appearance** are available as CSS variables: `--so-primary`,
  `--so-button-text`, `--so-bg`, `--so-surface`, `--so-text`, `--so-input-bg`, `--so-input-text`,
  `--so-input-border`, `--so-muted` and `--so-radius`. Use them (with a fallback) so one theme fits several
  applications: `background: var(--so-primary, #0f766e);`

Relative addresses in `theme.css` point into the theme, so `url("assets/logo.svg")` is `/themes/acme/assets/logo.svg`.

## Wording and languages: messages

A messages file maps the English text on the page to your text:

```json
{
  "Sign in to {1}": "Welcome back to {1}",
  "Sign in with password": "Continue",
  "Forgot password": "Forgot your password?"
}
```

- `messages/en.json` changes the English pages. `messages/hi.json`, `messages/fr.json` and so on change those languages,
  replacing SecureOne's own translation of that text. The file name's first part is the language (`pt-BR.json`
  counts as `pt`).
- The key is always the English text exactly as SecureOne shows it, without spaces around it. Texts the page shows
  later (messages after a button is pressed, dialogs) are matched the same way.
- Some texts contain a value, such as the heading "Sign in to *Acme Shop*". Write the value as `{1}` (and `{2}`, …
  for more): `"Sign in to {1}": "Welcome to {1}"`. In your text, `{1}` can go anywhere, or be left out.
- The window title is matched as a whole, for example `"Sign in · SecureOne": "Acme sign-in"`.
- A language listed in `theme.json` `languages` appears in the page's language picker. Texts you did not translate
  stay in English.
- With a parent theme, the parent's texts apply first and yours replace them.

## Images, fonts and scripts: assets

Files under `assets/` are served at `/themes/<theme>/assets/…`, from your theme or its parent. Allowed types:
images (`svg`, `png`, `jpg`, `gif`, `webp`, `avif`, `ico`), fonts (`woff2`, `woff`, `ttf`, `otf`), `css`, `js`, `json`
and `txt`. HTML files are never served from `assets/`.

`assets/favicon.svg` replaces the page icon.

The pages refuse scripts from other sites, to protect what people type into them: put fonts and scripts in
`assets/`, not on a CDN. Images may also come from any `https://` address.

## Replacing a whole page: pages/

Copy the built-in page out of the image and edit it:

```bash
docker cp secureone:/opt/secureone/theme-base/pages/login.html ./themes/acme/pages/login.html
docker cp secureone:/opt/secureone/theme-base/pages/account/signup.html ./themes/acme/pages/account/signup.html
```

Pages you can replace: `login.html`, and in `account/`: `signup.html`, `forgot-password.html`,
`reset-password.html`, `set-password.html`, `magic-link.html`, `confirm-email.html`, `device.html`,
`security.html` and `privacy.html`. A theme may also add pages of its own, such as `pages/account/terms.html`, served at
`/account/terms.html`.

Rules for a page copy:

- **Keep every element that has an `id`, and the `<script>` tags.** The page's script finds the form, fields,
  buttons and message areas by their `id`. Move them, wrap them and restyle them freely.
- Keep `/account/i18n.js` and `/account/hosted.js` in the `<head>`. They translate the page, apply the application's
  colours and logo, and show CAPTCHA when Attack protection asks for it.
- You do not add your `theme.css` or messages yourself: SecureOne adds them to every page, including your copies.
- Set `"secureone"` in `theme.json` to the version you copied the pages from.

## The pages SecureOne builds: templates/

SecureOne writes some pages itself: the consent screen, error pages and the short SAML pages. Their layout is
`templates/page.html`. Copy it from `/opt/secureone/theme-base/templates/page.html` to change the frame around every
such page, or give one page its own template, `templates/<name>.html`:

| Name | Page | Extra values |
|---|---|---|
| `consent` | "Allow *app*?" for third-party apps | `appName` |
| `oauth-error` | "This app cannot sign you in right now" | `errorCode`, `explanation`, `requestId` |
| `email-verified` | Email verified | `email` |
| `email-verify-failed` | Verification link invalid or expired | |
| `magic-link-invalid` | Invalid or expired sign-in link | `loginUrl` |
| `saml-error` | SAML sign-in and sign-out messages | `message` |
| `saml-post` | "Signing in…" while the browser forwards a SAML message | |

In a template, `{{name}}` is replaced with the value (HTML-escaped). Every template also has `lang`, `page` (the name
above), `title` and `heading`, plus two prepared parts that you must keep: `{{{content}}}` (the page's text and form)
and `{{{head}}}` (SecureOne's scripts, inside `<head>`).

Each folder in the chain is checked in turn, starting with your theme: its `templates/<name>.html`, then its
`templates/page.html`, then the parent's.

## Choosing a theme

A page uses, in this order:

1. `?theme=<name>` in the address (the console's **Preview**; only installed themes).
2. The application's choice under **Settings → Appearance → Sign-in page theme**. The page knows its application
   from `applicationId`, or from the sign-in request it belongs to.
3. `SECUREONE_THEME`.
4. `base`, the built-in pages.

A theme that is chosen but missing (for example removed from the folder) falls back to the next step, so a
sign-in page is never broken by a missing theme.

## Editing while SecureOne runs

| `SECUREONE_THEME_CACHE` | Default | After you change a theme file |
|---|---|---|
| `true` | in production | Press **Reload themes** (Platform settings → Appearance), or restart |
| `false` | otherwise | Shown on the next page load (within a second) |

**Platform settings → Appearance → Sign-in themes** shows each theme, what it changes, its parent, and any problem:
a folder name that is not allowed, a missing parent, a broken `theme.json`, or page copies that may be out of date.
The same problems are written to the log when SecureOne starts.

## When SecureOne is upgraded

Themes made of styles and messages keep working. For copied pages and templates, SecureOne compares the version in
your `theme.json` with the version in which each built-in page last changed. When the built-in page is newer, the
theme shows **may be out of date** in the console, and the log names the page. Copy the new built-in page from
`/opt/secureone/theme-base/` again, make your changes again, then raise `"secureone"` in `theme.json`.

## Kubernetes

With the Helm chart, put each theme in a ConfigMap and list it under `themes.configMaps`. ConfigMap keys cannot
contain `/`, so files in sub-folders are mapped with `items`:

```bash
kubectl create configmap acme-theme \
  --from-file=theme.json=themes/acme/theme.json \
  --from-file=theme.css=themes/acme/theme.css \
  --from-file=messages-en.json=themes/acme/messages/en.json \
  --from-file=logo.svg=themes/acme/assets/logo.svg
```

```yaml
themes:
  default: acme
  configMaps:
    - name: acme
      configMap: acme-theme
      items:
        - { key: theme.json, path: theme.json }
        - { key: theme.css, path: theme.css }
        - { key: messages-en.json, path: messages/en.json }
        - { key: logo.svg, path: assets/logo.svg }
```

Or keep all themes on one volume and set `themes.existingClaim` to its PersistentVolumeClaim (`ReadOnlyMany`, or
`ReadWriteMany` when you edit it in place). Every SecureOne pod must see the same themes. With the cache on, use
**Reload themes** on each pod, or roll the deployment.

## What a theme cannot do

- Load scripts from other sites, or be framed by another site.
- Replace SecureOne's page scripts (`/account/*.js`). Add your own in `assets/` instead.
- Read files outside the themes folder. Links that point out of it are ignored.
- Change how sign-in works: which methods are offered follows each application's **Authentication** settings.

## Troubleshooting

| What you see | Check |
|---|---|
| The theme is not listed | The folder name (lowercase, no spaces), that the folder is mounted at `/opt/secureone/themes` (or `SECUREONE_THEMES_DIR`), then **Reload themes** |
| Changes do not show | **Reload themes** (cache on in production); the browser may also keep an old copy of `theme.css`, so reload the page |
| A text does not change | The key must be the English text exactly as on the page (check it with the page's language set to English). A text with a name in it needs `{1}` |
| The application's colours are gone | The theme has a `theme.css`, so it styles the page: use `var(--so-primary)` and the other variables |
| A copied page stopped working after an upgrade | The console shows **may be out of date**; copy the new built-in page again |
| The application's logo appears twice | Your `theme.css` adds a logo too: remove the application's **Logo URL**, or the `body::before` rule of the starter theme |
