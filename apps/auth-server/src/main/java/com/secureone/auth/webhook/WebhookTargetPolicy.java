package com.secureone.auth.webhook;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Where webhooks may be delivered. Blocks non-HTTP schemes and, unless explicitly allowed for
 * local development, any target that resolves to a loopback, private, link-local (cloud metadata),
 * CGNAT, unique-local or multicast address, so tenants cannot use webhooks to reach internal services.
 */
@Component
public class WebhookTargetPolicy {

    /** Events the platform publishes; "*" subscribes to all of them. */
    static final Set<String> KNOWN_EVENTS = WebhookEvents.subscribable();

    private final boolean allowPrivateTargets;

    public WebhookTargetPolicy(@Value("${secureone.webhooks.allow-private-targets:false}") boolean allowPrivateTargets) {
        this.allowPrivateTargets = allowPrivateTargets;
    }

    /** Validates a URL on create/update; throws 400 with a reason. Unresolvable hosts are re-checked at delivery. */
    public String validateForRegistration(String raw) {
        URI uri = parse(raw);
        try {
            assertPublic(uri.getHost());
        } catch (UnknownHostException ex) {
            // Allowed now; delivery resolves again and refuses private targets.
        }
        return uri.toString();
    }

    /** Re-checks at send time (defends against DNS rebinding). Returns a reason, or null when deliverable. */
    public String deliveryProblem(String raw) {
        try {
            assertPublic(parse(raw).getHost());
            return null;
        } catch (ResponseStatusException ex) {
            return ex.getReason();
        } catch (UnknownHostException ex) {
            return "host cannot be resolved";
        }
    }

    static void validateEvents(Iterable<String> events) {
        for (String event : events) {
            if (!KNOWN_EVENTS.contains(event)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown webhook event '" + event + "'. Supported: " + String.join(", ", KNOWN_EVENTS.stream().sorted().toList()));
            }
        }
    }

    private static URI parse(String raw) {
        URI uri;
        try {
            uri = new URI(raw == null ? "" : raw.trim());
        } catch (Exception ex) {
            throw bad("url is not a valid URI.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw bad("url must use https (or http).");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw bad("url must include a host.");
        }
        if (uri.getRawUserInfo() != null) {
            throw bad("url must not contain credentials.");
        }
        if (uri.getRawFragment() != null) {
            throw bad("url must not contain a fragment.");
        }
        return uri;
    }

    private void assertPublic(String host) throws UnknownHostException {
        String h = host.toLowerCase(Locale.ROOT);
        if (allowPrivateTargets) {
            // Private networks are allowed, but never the cloud metadata service (instance credentials) or other
            // link-local addresses.
            if (h.equals("metadata.google.internal") || h.equals("metadata") || h.endsWith(".metadata.google.internal")) {
                throw bad("url points to the cloud metadata service.");
            }
            for (InetAddress address : InetAddress.getAllByName(h.replace("[", "").replace("]", ""))) {
                if (isMetadataOrLinkLocal(address)) {
                    throw bad("url points to the cloud metadata service or a link-local address ("
                            + address.getHostAddress() + ").");
                }
            }
            return;
        }
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) {
            throw bad("url points to an internal host.");
        }
        for (InetAddress address : InetAddress.getAllByName(h.replace("[", "").replace("]", ""))) {
            if (isInternal(address)) {
                throw bad("url resolves to an internal address (" + address.getHostAddress() + ").");
            }
        }
    }

    /**
     * Link-local addresses (169.254.0.0/16, fe80::/10: the metadata service of AWS, Google Cloud, Azure and others),
     * Azure's host address 168.63.129.16 and AWS's IPv6 metadata address fd00:ec2::254. Refused even when private
     * targets are allowed.
     */
    static boolean isMetadataOrLinkLocal(InetAddress address) {
        if (address.isLinkLocalAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return (b[0] & 0xff) == 168 && (b[1] & 0xff) == 63 && (b[2] & 0xff) == 129 && (b[3] & 0xff) == 16;
        }
        if (address instanceof Inet6Address) {
            byte[] awsMetadata = {(byte) 0xfd, 0, 0x0e, (byte) 0xc2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02, 0x54};
            return java.util.Arrays.equals(b, awsMetadata);
        }
        return false;
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0 // "this" network
                    || (first == 100 && second >= 64 && second <= 127) // CGNAT 100.64/10
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0/24 IETF
                    || first >= 240; // reserved / broadcast
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc; // unique local fc00::/7
        }
        return false;
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
