import NextAuth from "next-auth";
import { authOptions } from "@/lib/auth";

// Handles /api/auth/signin, /api/auth/callback/secureone, /api/auth/signout, /api/auth/session.
const handler = NextAuth(authOptions);
export { handler as GET, handler as POST };
