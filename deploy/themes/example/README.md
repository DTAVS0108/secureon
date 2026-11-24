# Starter sign-in theme

Copy this folder into your themes folder under a new name, then change it:

| File | What it does |
| --- | --- |
| `theme.json` | `parent`: the theme this one builds on (`base` = SecureOne's pages). `secureone`: the SecureOne version you made the theme for. `description`: shown in the console. |
| `theme.css` | Added to every hosted page after the page's own styles. |
| `messages/en.json` | Your wording: `"text on the page": "your text"`. Add `messages/hi.json`, `messages/fr.json`… for other languages. |
| `assets/` | Images, fonts and scripts, served at `/themes/<theme name>/assets/…`. `assets/favicon.svg` becomes the page icon. |
| `pages/` (optional) | Your own copy of a page, such as `pages/login.html` or `pages/account/signup.html`. Start from the built-in page in `/opt/secureone/theme-base/pages/`. |
| `templates/` (optional) | Layout of the pages built on the server (consent, errors): `templates/page.html`. Start from `/opt/secureone/theme-base/templates/page.html`. |

Delete what you do not need: anything missing comes from the parent theme.
