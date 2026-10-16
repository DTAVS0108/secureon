import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { SESSION_COOKIE } from "@/lib/auth/session-shared";

const PUBLIC_PREFIXES = ["/login", "/api/auth/login", "/api/auth/logout", "/setup", "/api/setup"];

function withPathname(request: NextRequest, pathname: string, response?: NextResponse) {
  const res = response ?? NextResponse.next({ request });
  res.headers.set("x-pathname", pathname);
  return res;
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (PUBLIC_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`))) {
    return withPathname(request, pathname);
  }
  const session = request.cookies.get(SESSION_COOKIE)?.value;
  if (!session) {
    const login = request.nextUrl.clone();
    login.pathname = "/login";
    if (pathname !== "/") {
      login.searchParams.set("next", pathname);
    }
    return withPathname(request, pathname, NextResponse.redirect(login));
  }
  return withPathname(request, pathname);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
