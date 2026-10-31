---
title: Custom domains
summary: Serve a tenant's sign-in on its own domain
order: 5
audience: operator
---

# Custom domains

Customers expect to sign in at their own address (for example `login.example.com`), not at a vendor's. SecureOne supports one public domain per installation: every URL it issues, and every token's `iss`, uses it.

## 1. DNS and TLS

1. Create a DNS record for the domain (for example `login.example.com`) pointing at your reverse proxy or load balancer.
2. Get a certificate for it (Let's Encrypt or your CA) on the proxy. SecureOne itself listens on plain HTTP behind the proxy.
3. Forward requests to the SecureOne container (port 9000), keeping the original host and scheme:
   - `Host` (or `X-Forwarded-Host`) and `X-Forwarded-Proto: https`
   - `X-Forwarded-For` with the client address (attack protection, rate limits and audit use it)

nginx example:

```nginx
server {
  listen 443 ssl http2;
  server_name login.example.com;
  ssl_certificate     /etc/ssl/login.example.com/fullchain.pem;
  ssl_certificate_key /etc/ssl/login.example.com/privkey.pem;
  location / {
    proxy_pass http://secureone:9000;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }
}
```

## 2. SecureOne settings (container variables)

```
SECUREONE_PUBLIC_BASE_URL=https://login.example.com     # also the issuer, unless SECUREONE_ISSUER_URL is set
# The proxy's address as SecureOne sees it (or its range: /8, /16, /24 or /32), comma-separated.
SECUREONE_TRUSTED_PROXIES=10.0.0.5
```

`SECUREONE_TRUSTED_PROXIES` is required behind a proxy. SecureOne reads `X-Forwarded-For` and `X-Forwarded-Proto` only from loopback and these addresses. From anyone else it ignores them, so a client cannot pick its own address to get past IP rules, rate limits or the CAPTCHA failure count. If the variable is missing, every user appears to come from the proxy's address, so rate limits and blocks hit everyone at once. To find the address, sign in and open **Sign-in history**: the address shown is the one to list. With Docker, it is usually the gateway of the network the proxy container is on.

Restart the container. Check `https://login.example.com/.well-known/openid-configuration`: `issuer` and every endpoint must start with the new domain.

## 3. Applications

- Update each OAuth client's issuer / discovery URL to the new domain (`https://login.example.com`).
- Tokens issued before the change carry the old `iss`; apps that check the issuer reject them, so users sign in once more.
- Password-reset and magic-link pages on the app's own site must still be on the app's OAuth redirect origins (Integration › client URLs).

## Several domains on one installation

One installation answers on one domain. A different domain per tenant (each with its own issuer) is not supported yet: it needs per-host issuers in the authorization server and a host allow-list. Until then, run one installation per domain, or put all tenants on one shared domain.
