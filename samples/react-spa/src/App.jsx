import { useState } from "react";
import { useAuth } from "react-oidc-context";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:4000";

export default function App() {
  const auth = useAuth();
  const [result, setResult] = useState(null);

  /** Calls the sample API with the user's access token. */
  async function call(path) {
    const res = await fetch(`${API_URL}${path}`, {
      headers: { Authorization: `Bearer ${auth.user?.access_token}` },
    });
    setResult({ path, status: res.status, body: await res.json().catch(() => null) });
  }

  if (auth.isLoading) return <main><p>Loading…</p></main>;

  return (
    <main>
      <h1>Demo Store</h1>
      {auth.error && <p className="error" role="alert">Sign-in failed: {auth.error.message}</p>}

      {!auth.isAuthenticated ? (
        <>
          <p>Sign in with your SecureOne account.</p>
          <button onClick={() => auth.signinRedirect()}>Sign in</button>
        </>
      ) : (
        <>
          <p data-testid="signed-in">
            Signed in as <strong>{auth.user?.profile.email}</strong>
          </p>
          <div className="row">
            <button onClick={() => call("/api/me")}>Call API: /api/me</button>
            <button onClick={() => call("/api/orders")}>Call API: /api/orders</button>
            <button className="secondary" onClick={() => auth.signoutRedirect()}>Sign out</button>
          </div>
          {result && (
            <section>
              <p>
                GET {result.path} → <strong data-testid="status">{result.status}</strong>
              </p>
              <pre data-testid="body">{JSON.stringify(result.body, null, 2)}</pre>
            </section>
          )}
        </>
      )}
    </main>
  );
}
