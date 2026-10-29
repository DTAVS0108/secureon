import Link from "next/link";
import { DocsShell } from "@/components/documentation/DocsShell";
import { ArrowRightIcon, LockIcon, SpaceIcon } from "@/components/documentation/DocsIcons";
import { flatten, getSpaces } from "@/lib/documentation/catalog";
import { docsRootExists } from "@/lib/documentation/loader";
import { getDocsViewer } from "@/lib/documentation/viewer";

export const dynamic = "force-dynamic";

const START_HERE = [
  { title: "Try it", text: "SecureOne running on your machine in five minutes.", slug: "get-started/quickstart" },
  { title: "Install", text: "Run the image with your database, secrets and domain.", slug: "install/deployment" },
  { title: "Integrate", text: "Add sign-in and roles to your application.", slug: "integrate/integration-guide" },
  { title: "Use the console", text: "Every page, button and field explained.", slug: "guide" },
];

export default async function DocsHomePage() {
  if (!(await docsRootExists())) {
    return <p className="p-8 text-sm">Documentation folder not found (docs/ or SECUREONE_DOCS_DIR).</p>;
  }
  const viewer = await getDocsViewer();
  const spaces = await getSpaces(viewer);
  const hasLocked = spaces.some((space) => flatten([space]).some((n) => n.locked));

  return (
    <DocsShell spaces={spaces} activeSlug="" viewer={viewer}>
      <div className="mx-auto max-w-5xl">
        <section className="docs-hero relative overflow-hidden rounded-3xl px-8 py-12 sm:px-12">
          <p className="text-sm font-medium text-white/80">SecureOne Documentation</p>
          <h1 className="mt-2 max-w-2xl text-3xl font-semibold tracking-tight text-white sm:text-4xl">
            Everything you need to run SecureOne and connect your applications
          </h1>
          <p className="mt-4 max-w-2xl text-[15px] leading-relaxed text-white/85">
            Install it, set it up, add sign-in to your apps and find every API, organised in spaces. Press{" "}
            <kbd className="rounded border border-white/40 px-1.5 py-0.5 text-xs">Ctrl K</kbd> to search every page.
          </p>
        </section>

        <section className="mt-10">
          <h2 className="mb-4 text-lg font-semibold text-ui">Start here</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {START_HERE.map((item) => (
              <Link
                key={item.slug}
                href={`/documentation/${item.slug}`}
                className="group rounded-2xl border border-ui p-5 transition-all hover:-translate-y-0.5 hover:border-[var(--ui-primary)] hover:shadow-md"
              >
                <span className="flex items-center justify-between text-[15px] font-semibold text-ui group-hover:text-brand">
                  {item.title}
                  <ArrowRightIcon className="h-4 w-4 opacity-0 transition-opacity group-hover:opacity-100" />
                </span>
                <span className="mt-1.5 block text-[13px] leading-snug text-muted">{item.text}</span>
              </Link>
            ))}
          </div>
        </section>

        <section className="mt-12">
          <h2 className="mb-4 text-lg font-semibold text-ui">Spaces</h2>
          <div className="grid gap-4 md:grid-cols-2">
            {spaces.map((space) => {
              const pages = flatten(space.children).filter((n) => n.kind === "page");
              return (
                <div key={space.slug} className="flex flex-col rounded-2xl border border-ui p-6 transition-shadow hover:shadow-md">
                  <div className="flex items-start gap-3">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-muted text-brand">
                      <SpaceIcon slug={space.slug} className="h-5 w-5" />
                    </span>
                    <div className="min-w-0">
                      <Link href={`/documentation/${space.slug}`} className="text-base font-semibold text-ui hover:text-brand">
                        {space.title}
                      </Link>
                      <p className="mt-0.5 text-[13px] leading-snug text-muted">{space.summary}</p>
                    </div>
                  </div>
                  <ul className="mt-4 space-y-1.5 text-[13px]">
                    {space.children.slice(0, 5).map((child) => (
                      <li key={child.slug}>
                        <Link href={`/documentation/${child.slug}`} className="inline-flex items-center gap-1.5 text-soft hover:text-brand">
                          {child.title}
                          {child.locked && <LockIcon className="h-3 w-3 text-faint" />}
                        </Link>
                      </li>
                    ))}
                  </ul>
                  <Link
                    href={`/documentation/${space.slug}`}
                    className="mt-auto inline-flex items-center gap-1 pt-4 text-[13px] font-medium text-brand"
                  >
                    {pages.length} pages <ArrowRightIcon className="h-3.5 w-3.5" />
                  </Link>
                </div>
              );
            })}
          </div>
          {hasLocked && !viewer.signedIn && (
            <p className="mt-5 flex items-center gap-1.5 text-xs text-muted">
              <LockIcon className="h-3.5 w-3.5" /> Pages with a lock are for operators: sign in to the console to read them.
            </p>
          )}
        </section>
      </div>
    </DocsShell>
  );
}
