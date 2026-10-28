package com.secureone.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureValidator;

class SamlIdpMessagesTest {

    private static SamlIdpCredentialService.Credential credential() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=test");
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(
                name, BigInteger.ONE, Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(1))), name, pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        return new SamlIdpCredentialService.Credential(pair.getPrivate(), cert);
    }

    private static Response parse(String base64) throws Exception {
        return (Response) XMLObjectSupport.unmarshallFromInputStream(
                XMLObjectProviderRegistrySupport.getParserPool(), new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }

    private static SamlIdpMessages.Assertion assertion() {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        attributes.put("email", List.of("ann@example.com"));
        attributes.put("roles", List.of("admin", "viewer"));
        return new SamlIdpMessages.Assertion("https://idp/saml2/idp/tenant-a", "https://sp.example.com", "https://sp.example.com/acs",
                "_req1", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress", "ann@example.com", Instant.now(),
                "_s1", true, attributes, 300);
    }

    @Test
    void successResponseIsSignedTwiceAndCarriesTheAssertion() throws Exception {
        SamlIdpCredentialService.Credential credential = credential();
        Response response = parse(SamlIdpMessages.successResponse(assertion(), credential, true, true));
        BasicX509Credential verification = new BasicX509Credential(credential.certificate());
        new SAMLSignatureProfileValidator().validate(response.getSignature());
        SignatureValidator.validate(response.getSignature(), verification);
        Assertion a = response.getAssertions().get(0);
        new SAMLSignatureProfileValidator().validate(a.getSignature());
        SignatureValidator.validate(a.getSignature(), verification);
        assertEquals(StatusCode.SUCCESS, response.getStatus().getStatusCode().getValue());
        assertEquals("_req1", response.getInResponseTo());
        assertEquals("https://sp.example.com/acs", response.getDestination());
        assertEquals("ann@example.com", a.getSubject().getNameID().getValue());
        assertEquals("https://sp.example.com/acs", a.getSubject().getSubjectConfirmations().get(0).getSubjectConfirmationData().getRecipient());
        assertEquals("https://sp.example.com", a.getConditions().getAudienceRestrictions().get(0).getAudiences().get(0).getURI());
        assertEquals(SamlIdpMessages.MFA_CONTEXT, a.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef().getURI());
        assertEquals(2, a.getAttributeStatements().get(0).getAttributes().get(1).getAttributeValues().size());
        assertTrue(response.getID().startsWith("_"));
    }

    @Test
    void aTamperedAssertionFailsItsSignature() throws Exception {
        SamlIdpCredentialService.Credential credential = credential();
        String xml = new String(Base64.getDecoder().decode(
                SamlIdpMessages.successResponse(assertion(), credential, false, true)), StandardCharsets.UTF_8);
        String forged = xml.replace("ann@example.com", "user-c@example.com");
        Response response = parse(Base64.getEncoder().encodeToString(forged.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> SignatureValidator.validate(
                response.getAssertions().get(0).getSignature(), new BasicX509Credential(credential.certificate())));
    }

    @Test
    void errorResponseNamesTheStatus() throws Exception {
        Response response = parse(SamlIdpMessages.errorResponse("https://idp", "https://sp/acs", "_r", StatusCode.REQUEST_DENIED,
                "no access", credential()));
        assertEquals(StatusCode.RESPONDER, response.getStatus().getStatusCode().getValue());
        assertEquals(StatusCode.REQUEST_DENIED, response.getStatus().getStatusCode().getStatusCode().getValue());
        assertTrue(response.getAssertions().isEmpty());
    }

    private static final String AUTHN_REQUEST = "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            + "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ID=\"_abc\" Version=\"2.0\" IssueInstant=\"2026-01-01T00:00:00Z\" "
            + "AssertionConsumerServiceURL=\"https://sp/acs\"><saml:Issuer>https://sp</saml:Issuer></samlp:AuthnRequest>";

    private static String deflated(String xml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, true))) {
            deflater.write(xml.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void readsRedirectAndPostBindingRequests() throws Exception {
        AuthnRequest redirect = assertInstanceOf(AuthnRequest.class, SamlIdpMessages.decodeRequest(deflated(AUTHN_REQUEST), true));
        assertEquals("https://sp", redirect.getIssuer().getValue());
        assertEquals("https://sp/acs", redirect.getAssertionConsumerServiceURL());
        AuthnRequest post = assertInstanceOf(AuthnRequest.class, SamlIdpMessages.decodeRequest(
                Base64.getEncoder().encodeToString(AUTHN_REQUEST.getBytes(StandardCharsets.UTF_8)), false));
        assertEquals("_abc", post.getID());
        assertThrows(IllegalArgumentException.class, () -> SamlIdpMessages.decodeRequest("not base64!", true));
        assertThrows(IllegalArgumentException.class, () -> SamlIdpMessages.decodeRequest(
                Base64.getEncoder().encodeToString("<x/>".getBytes(StandardCharsets.UTF_8)), false));
    }

    @Test
    void redirectBindingSignatureIsCheckedOverTheQueryAsSent() throws Exception {
        SamlIdpCredentialService.Credential sp = credential();
        String request = "SAMLRequest=" + URLEncoder.encode(deflated(AUTHN_REQUEST), StandardCharsets.UTF_8);
        String relay = "RelayState=" + URLEncoder.encode("/home?a=1", StandardCharsets.UTF_8);
        String sigAlg = "SigAlg=" + URLEncoder.encode(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256, StandardCharsets.UTF_8);
        java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(sp.privateKey());
        signer.update((request + "&" + relay + "&" + sigAlg).getBytes(StandardCharsets.UTF_8));
        String signature = "Signature=" + URLEncoder.encode(Base64.getEncoder().encodeToString(signer.sign()), StandardCharsets.UTF_8);
        String query = request + "&" + relay + "&" + sigAlg + "&" + signature;
        assertTrue(SamlIdpMessages.redirectSignatureValid(query, sp.certificate()));
        assertFalse(SamlIdpMessages.redirectSignatureValid(query.replace("a%3D1", "a%3D2"), sp.certificate()));
        assertFalse(SamlIdpMessages.redirectSignatureValid(query, credential().certificate()));
        String sha1 = query.replace(URLEncoder.encode(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256, StandardCharsets.UTF_8),
                URLEncoder.encode(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1, StandardCharsets.UTF_8));
        assertFalse(SamlIdpMessages.redirectSignatureValid(sha1, sp.certificate()), "SHA-1 is refused");
    }

    @Test
    void logoutResponseIsSigned() throws Exception {
        SamlIdpCredentialService.Credential credential = credential();
        var response = (org.opensaml.saml.saml2.core.LogoutResponse) XMLObjectSupport.unmarshallFromInputStream(
                XMLObjectProviderRegistrySupport.getParserPool(), new ByteArrayInputStream(Base64.getDecoder().decode(
                        SamlIdpMessages.logoutResponse("https://idp", "https://sp/slo", "_lr", credential))));
        assertNotNull(response.getSignature());
        SignatureValidator.validate(response.getSignature(), new BasicX509Credential(credential.certificate()));
        assertEquals("_lr", response.getInResponseTo());
    }
}
