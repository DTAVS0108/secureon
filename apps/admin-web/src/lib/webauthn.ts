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
  const parsed = JSON.parse(publicKeyJson) as CreationOptionsJson;
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
