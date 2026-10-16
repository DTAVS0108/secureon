import { getServerSession } from "next-auth";
import { authOptions } from "@/lib/auth";
import { SignInButton, SignOutButton } from "./buttons";

// Rendered on the server: the session is read from NextAuth's encrypted cookie.
export default async function Home() {
  const session = await getServerSession(authOptions);

  return (
    <main>
      <h1>Demo Store</h1>
      {session ? (
        <>
          <p data-testid="signed-in">
            Signed in as <strong>{session.user.email}</strong>
            {session.user.name && session.user.name !== session.user.email ? ` (${session.user.name})` : ""}
          </p>
          <SignOutButton />
        </>
      ) : (
        <>
          <p>Sign in with your SecureOne account.</p>
          <SignInButton />
        </>
      )}
    </main>
  );
}
