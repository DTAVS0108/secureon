import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider } from "react-oidc-context";
import App from "./App.jsx";
import "./styles.css";

const oidcConfig = {
  authority: import.meta.env.VITE_SECUREONE_ISSUER,
  client_id: import.meta.env.VITE_SECUREONE_CLIENT_ID,
  redirect_uri: window.location.origin + "/",
  post_logout_redirect_uri: window.location.origin + "/",
  scope: "openid profile email",
  // Renew the access token with the refresh token before it expires.
  automaticSilentRenew: true,
  // Remove ?code=&state= from the address bar once sign-in completes.
  onSigninCallback: () => window.history.replaceState({}, document.title, window.location.pathname),
};

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AuthProvider {...oidcConfig}>
      <App />
    </AuthProvider>
  </StrictMode>,
);
