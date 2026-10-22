function bufferToBase64url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64urlToUint8Array(value: string): Uint8Array<ArrayBuffer> {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/");
  const pad = padded.length % 4 === 0 ? "" : "=".repeat(4 - (padded.length % 4));
  const binary = atob(padded + pad);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

type CreationOptionsJson = {
  challenge: string;
  user: { id: string; name: string; displayName: string };
  excludeCredentials?: Array<{ id: string; type: string; transports?: string[] }>;
  [key: string]: unknown;
};

function decodeCreationOptions(publicKeyJson: string): PublicKeyCredentialCreationOptions {
  // The server sends navigator.credentials.create() input: { publicKey: {...} }.
  const raw = JSON.parse(publicKeyJson) as { publicKey?: CreationOptionsJson } & CreationOptionsJson;
  const parsed = (raw.publicKey ?? raw) as CreationOptionsJson;
  const challenge = base64urlToUint8Array(parsed.challenge);
  const user: PublicKeyCredentialUserEntity = {
    id: base64urlToUint8Array(parsed.user.id),
    name: parsed.user.name,
    displayName: parsed.user.displayName,
  };
  const excludeCredentials = parsed.excludeCredentials?.map((cred) => ({
    id: base64urlToUint8Array(cred.id),
    type: "public-key" as PublicKeyCredentialType,
    transports: cred.transports as AuthenticatorTransport[] | undefined,
  }));
  return {
    ...(parsed as unknown as PublicKeyCredentialCreationOptions),
    challenge,
    user,
    excludeCredentials,
  };
}

export function credentialToRegistrationJson(credential: PublicKeyCredential): string {
  const response = credential.response as AuthenticatorAttestationResponse;
  return JSON.stringify({
    id: credential.id,
    rawId: bufferToBase64url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64url(response.clientDataJSON),
      attestationObject: bufferToBase64url(response.attestationObject),
      transports: response.getTransports?.() ?? [],
    },
    clientExtensionResults: credential.getClientExtensionResults?.() ?? {},
  });
}

export async function createPasskey(publicKeyJson: string): Promise<string> {
  if (!window.PublicKeyCredential) {
    throw new Error("Passkeys are not supported in this browser.");
  }
  const publicKey = decodeCreationOptions(publicKeyJson);
  const credential = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential | null;
  if (!credential) {
    throw new Error("Passkey registration was cancelled.");
  }
  return credentialToRegistrationJson(credential);
}

export function passkeysSupported(): boolean {
  return typeof window !== "undefined" && Boolean(window.PublicKeyCredential);
}

type RequestOptionsJson = {
  challenge: string;
  allowCredentials?: Array<{ id: string; type: string; transports?: string[] }>;
  [key: string]: unknown;
};

/** Signs a server challenge with a passkey or security key; returns the JSON the server verifies. */
export async function signWithPasskey(publicKeyJson: string): Promise<string> {
  if (!window.PublicKeyCredential) {
    throw new Error("Passkeys are not supported in this browser.");
  }
  const raw = JSON.parse(publicKeyJson) as { publicKey?: RequestOptionsJson } & RequestOptionsJson;
  const parsed = (raw.publicKey ?? raw) as RequestOptionsJson;
  const publicKey: PublicKeyCredentialRequestOptions = {
    ...(parsed as unknown as PublicKeyCredentialRequestOptions),
    challenge: base64urlToUint8Array(parsed.challenge),
    allowCredentials: parsed.allowCredentials?.map((cred) => ({
      id: base64urlToUint8Array(cred.id),
      type: "public-key" as PublicKeyCredentialType,
      transports: cred.transports as AuthenticatorTransport[] | undefined,
    })),
  };
  const credential = (await navigator.credentials.get({ publicKey })) as PublicKeyCredential | null;
  if (!credential) {
    throw new Error("The passkey was not used.");
  }
  const response = credential.response as AuthenticatorAssertionResponse;
  return JSON.stringify({
    id: credential.id,
    rawId: bufferToBase64url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64url(response.clientDataJSON),
      authenticatorData: bufferToBase64url(response.authenticatorData),
      signature: bufferToBase64url(response.signature),
      userHandle: response.userHandle ? bufferToBase64url(response.userHandle) : null,
    },
    clientExtensionResults: credential.getClientExtensionResults?.() ?? {},
  });
}
