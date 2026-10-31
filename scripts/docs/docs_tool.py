"""Checks the documentation and rebuilds docs/README.md (the index shown on GitHub).

Every page under docs/ (except docs/README.md) starts with front matter:

    ---
    title: Deployment
    summary: One line shown in menus and search
    order: 2
    audience: public        # public | operator | internal
    ---

A folder is a space (top level) or a section; its index.md describes it. Inside any page, content between
<!-- developer --> and <!-- /developer --> (local setup, source builds, repository paths) is shown only to signed-in
developers in the console and never published on the website. Run:

    python scripts/docs/docs_tool.py check      # front matter, links, audience rules
    python scripts/docs/docs_tool.py index      # rewrite docs/README.md
"""

from __future__ import annotations

import posixpath
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

DOCS = Path(__file__).resolve().parents[2] / "docs"
AUDIENCES = ("public", "operator", "internal")
FRONT = re.compile(r"^﻿?---\r?\n([\s\S]*?)\r?\n---\r?\n")
LINK = re.compile(r"(?<!!)\[([^\]]*)\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
CODE_FENCE = re.compile(r"^\s*```")


@dataclass
class Page:
    path: str  # relative to docs/, posix
    title: str
    summary: str
    order: int
    audience: str
    body: str
    children: list["Page"] = field(default_factory=list)


def read_pages() -> dict[str, Page]:
    pages = {}
    for file in sorted(DOCS.rglob("*.md")):
        rel = file.relative_to(DOCS).as_posix()
        if rel == "README.md":
            continue
        raw = file.read_text(encoding="utf-8")
        m = FRONT.match(raw)
        fields = {}
        if m:
            for line in m.group(1).splitlines():
                key, _, value = line.partition(":")
                fields[key.strip()] = value.strip()
        try:
            order = int(fields.get("order", "999"))
        except ValueError:
            order = 999
        pages[rel] = Page(rel, fields.get("title", ""), fields.get("summary", ""), order,
                          fields.get("audience", ""), raw[m.end():] if m else raw)
    return pages


def links(page: Page):
    in_code = False
    for line in page.body.splitlines():
        if CODE_FENCE.match(line):
            in_code = not in_code
            continue
        if in_code:
            continue
        for m in LINK.finditer(line):
            yield m.group(2)


def check() -> int:
    pages = read_pages()
    errors, warnings = [], []
    rank = {a: i for i, a in enumerate(AUDIENCES)}
    for page in pages.values():
        if not page.title:
            errors.append(f"{page.path}: missing title")
        if page.audience not in AUDIENCES:
            errors.append(f"{page.path}: audience must be one of {', '.join(AUDIENCES)}")
        if not page.summary:
            warnings.append(f"{page.path}: no summary")
        if re.match(r"^\d", page.title):
            errors.append(f"{page.path}: title starts with a number")
        opens = len(re.findall(r"<!--\s*developer\s*-->", page.body))
        closes = len(re.findall(r"<!--\s*/developer\s*-->", page.body))
        if opens != closes:
            errors.append(f"{page.path}: {opens} <!-- developer --> but {closes} <!-- /developer -->")
        folder = posixpath.dirname(page.path)
        for href in links(page):
            if re.match(r"^(https?:|mailto:|#|/|\$)", href):
                continue
            target = posixpath.normpath(posixpath.join(folder, href.split("#")[0]))
            if target.startswith(".."):
                if not (DOCS.parent / posixpath.normpath(posixpath.join("docs", folder, href.split("#")[0]))).exists():
                    errors.append(f"{page.path}: broken link {href}")
                continue
            if target.endswith(".md"):
                dest = pages.get(target)
                if dest is None and target != "README.md":
                    errors.append(f"{page.path}: broken link {href}")
                elif dest and page.audience in rank and dest.audience in rank and rank[dest.audience] > rank[page.audience]:
                    warnings.append(f"{page.path}: {page.audience} page links to {dest.audience} page {target}")
            elif not (DOCS / target).exists():
                errors.append(f"{page.path}: broken link {href}")
    for top in sorted({p.split("/")[0] for p in pages if "/" in p}):
        if f"{top}/index.md" not in pages:
            errors.append(f"docs/{top}/: no index.md for this space")
    for w in warnings:
        print("note:", w)
    for e in errors:
        print("error:", e)
    print(f"{len(pages)} pages, {len(errors)} error(s), {len(warnings)} note(s)")
    return 1 if errors else 0


def tree(pages: dict[str, Page]) -> list[Page]:
    """Spaces (top-level index pages) with their pages and sections, sorted by order then title."""
    def kids(folder: str) -> list[Page]:
        out = []
        for path, page in pages.items():
            parent = posixpath.dirname(path)
            if path.endswith("/index.md"):
                if posixpath.dirname(parent) == folder and parent != folder:
                    page.children = kids(parent)
                    out.append(page)
            elif parent == folder:
                out.append(page)
        return sorted(out, key=lambda p: (p.order, p.title))

    spaces = []
    for path, page in pages.items():
        if path.count("/") == 1 and path.endswith("/index.md"):
            page.children = kids(posixpath.dirname(path))
            spaces.append(page)
    return sorted(spaces, key=lambda p: (p.order, p.title))


MARK = {"public": "", "operator": " *(operators)*", "internal": " *(developers)*"}


def index() -> None:
    lines = [
        "# SecureOne documentation",
        "",
        "Also in the console at `/admin/documentation`, with search. Pages marked *(operators)* need a console",
        "sign-in there; *(developers)* pages are for people working on SecureOne itself.",
        "",
        "Generated by `python scripts/docs/docs_tool.py index` from each page's front matter; do not edit by hand.",
        "",
    ]

    def emit(page: Page, depth: int) -> None:
        for child in page.children:
            target = child.path
            lines.append(f"{'  ' * depth}- [{child.title}]({target}){MARK.get(child.audience, '')}"
                         + (f": {child.summary}" if child.summary else ""))
            emit(child, depth + 1)

    for space in tree(read_pages()):
        lines += [f"## [{space.title}]({space.path}){MARK.get(space.audience, '')}", "", space.summary, ""]
        emit(space, 0)
        lines.append("")
    (DOCS / "README.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8", newline="\n")
    print("wrote docs/README.md")


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "check"
    if command == "index":
        index()
    sys.exit(check() if command == "check" else 0)
