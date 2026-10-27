package com.secureone.auth.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.xml.namespace.QName;
import net.shibboleth.shared.xml.SerializeSupport;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.schema.impl.XSStringBuilder;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.RequestAbstractType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.StatusMessage;
import org.opensaml.saml.saml2.core.StatusResponseType;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.SignableXMLObject;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureSupport;
import org.opensaml.xmlsec.signature.support.SignatureValidator;

/**
 * SAML 2.0 messages of the identity provider (OpenSAML): reading AuthnRequests and LogoutRequests (HTTP-Redirect
 * and HTTP-POST bindings, signatures checked against the SP's certificate), and writing signed Responses and
 * LogoutResponses. Signatures are RSA-SHA256 with SHA-256 digests and exclusive canonicalization.
 */
public final class SamlIdpMessages {

    public static final String BINDING_REDIRECT = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect";
    public static final String BINDING_POST = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST";
    static final String MFA_CONTEXT = "urn:oasis:names:tc:SAML:2.0:ac:classes:MobileTwoFactorContract";
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        org.springframework.security.saml2.core.OpenSamlInitializationService.initialize();
    }

    private SamlIdpMessages() {}

    /** What the IdP asserts about the signed-in user to one SP. */
    public record Assertion(
            String idpEntityId, String spEntityId, String acsUrl, String inResponseTo, String nameIdFormat,
            String nameId, Instant authnInstant, String sessionIndex, boolean twoStep,
            Map<String, List<String>> attributes, int lifetimeSeconds) {}

    /** "_" + 40 hex characters: an XML ID (it must not start with a digit). */
    public static String newId() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return "_" + HexFormat.of().formatHex(bytes);
    }

    // ---- reading ----

    /** The SAMLRequest of the HTTP-Redirect binding (base64 of DEFLATE) or the HTTP-POST binding (base64). */
    public static RequestAbstractType decodeRequest(String samlRequest, boolean redirect) {
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(samlRequest);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("SAMLRequest is not base64.");
        }
        if (redirect) {
            bytes = inflate(bytes);
        }
        XMLObject parsed;
        try {
            parsed = XMLObjectSupport.unmarshallFromInputStream(
                    XMLObjectProviderRegistrySupport.getParserPool(), new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("SAMLRequest is not a SAML message.");
        }
        if (parsed instanceof AuthnRequest || parsed instanceof LogoutRequest) {
            return (RequestAbstractType) parsed;
        }
        throw new IllegalArgumentException("SAMLRequest is neither an AuthnRequest nor a LogoutRequest.");
    }

    private static byte[] inflate(byte[] deflated) {
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(deflated), new Inflater(true))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (out.size() > 256 * 1024) {
                    throw new IllegalArgumentException("SAMLRequest is too large.");
                }
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("SAMLRequest is not DEFLATE-compressed.");
        }
    }

    /** An enveloped signature on a POSTed request, checked against the SP's certificate. */
    public static boolean postSignatureValid(SignableXMLObject message, X509Certificate spCertificate) {
        if (message.getSignature() == null) {
            return false;
        }
        try {
            new SAMLSignatureProfileValidator().validate(message.getSignature());
            SignatureValidator.validate(message.getSignature(), new BasicX509Credential(spCertificate));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The HTTP-Redirect binding's signature (SAML Bindings 3.4.4.1): over "SAMLRequest=..&RelayState=..&SigAlg=.." as
     * sent (URL-encoded), RSA-SHA256 (or SHA-384/512); SHA-1 is refused.
     */
    public static boolean redirectSignatureValid(String rawQuery, X509Certificate spCertificate) {
        if (rawQuery == null) {
            return false;
        }
        String request = null, relay = null, sigAlg = null, signature = null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            switch (key) {
                case "SAMLRequest" -> request = pair;
                case "RelayState" -> relay = pair;
                case "SigAlg" -> sigAlg = pair;
                case "Signature" -> signature = eq < 0 ? null : pair.substring(eq + 1);
                default -> { }
            }
        }
        if (request == null || sigAlg == null || signature == null) {
            return false;
        }
        String algorithm = switch (URLDecoder.decode(sigAlg.substring(sigAlg.indexOf('=') + 1), StandardCharsets.UTF_8)) {
            case SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256 -> "SHA256withRSA";
            case SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA384 -> "SHA384withRSA";
            case SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA512 -> "SHA512withRSA";
            default -> null;
        };
        if (algorithm == null) {
            return false;
        }
        String signed = request + (relay != null ? "&" + relay : "") + "&" + sigAlg;
        try {
            java.security.Signature verifier = java.security.Signature.getInstance(algorithm);
            verifier.initVerify(spCertificate.getPublicKey());
            verifier.update(signed.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getMimeDecoder().decode(URLDecoder.decode(signature, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return false;
        }
    }

    // ---- writing ----

    /** A Response with the signed assertion (and itself signed, as the SP is configured), base64 for the POST form. */
    public static String successResponse(Assertion a, SamlIdpCredentialService.Credential credential,
            boolean signResponse, boolean signAssertion) {
        Instant now = Instant.now();
        Response response = build(Response.DEFAULT_ELEMENT_NAME);
        response.setID(newId());
        response.setIssueInstant(now);
        response.setVersion(SAMLVersion.VERSION_20);
        response.setDestination(a.acsUrl());
        response.setInResponseTo(a.inResponseTo());
        response.setIssuer(issuer(a.idpEntityId()));
        response.setStatus(status(StatusCode.SUCCESS, null, null));

        org.opensaml.saml.saml2.core.Assertion assertion = build(org.opensaml.saml.saml2.core.Assertion.DEFAULT_ELEMENT_NAME);
        assertion.setID(newId());
        assertion.setIssueInstant(now);
        assertion.setVersion(SAMLVersion.VERSION_20);
        assertion.setIssuer(issuer(a.idpEntityId()));

        NameID nameId = build(NameID.DEFAULT_ELEMENT_NAME);
        nameId.setFormat(a.nameIdFormat());
        nameId.setValue(a.nameId());
        SubjectConfirmationData data = build(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
        data.setRecipient(a.acsUrl());
        data.setNotOnOrAfter(now.plusSeconds(a.lifetimeSeconds()));
        data.setInResponseTo(a.inResponseTo());
        SubjectConfirmation confirmation = build(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
        confirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
        confirmation.setSubjectConfirmationData(data);
        Subject subject = build(Subject.DEFAULT_ELEMENT_NAME);
        subject.setNameID(nameId);
        subject.getSubjectConfirmations().add(confirmation);
        assertion.setSubject(subject);

        Audience audience = build(Audience.DEFAULT_ELEMENT_NAME);
        audience.setURI(a.spEntityId());
        AudienceRestriction restriction = build(AudienceRestriction.DEFAULT_ELEMENT_NAME);
        restriction.getAudiences().add(audience);
        Conditions conditions = build(Conditions.DEFAULT_ELEMENT_NAME);
        // A minute of clock skew either side of the SP.
        conditions.setNotBefore(now.minusSeconds(60));
        conditions.setNotOnOrAfter(now.plusSeconds(a.lifetimeSeconds()));
        conditions.getAudienceRestrictions().add(restriction);
        assertion.setConditions(conditions);

        AuthnContextClassRef classRef = build(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
        classRef.setURI(a.twoStep() ? MFA_CONTEXT : AuthnContext.PPT_AUTHN_CTX);
        AuthnContext context = build(AuthnContext.DEFAULT_ELEMENT_NAME);
        context.setAuthnContextClassRef(classRef);
        AuthnStatement authn = build(AuthnStatement.DEFAULT_ELEMENT_NAME);
        authn.setAuthnInstant(a.authnInstant() != null ? a.authnInstant() : now);
        authn.setSessionIndex(a.sessionIndex());
        authn.setAuthnContext(context);
        assertion.getAuthnStatements().add(authn);

        if (!a.attributes().isEmpty()) {
            AttributeStatement statement = build(AttributeStatement.DEFAULT_ELEMENT_NAME);
            XSStringBuilder strings = (XSStringBuilder) XMLObjectProviderRegistrySupport.getBuilderFactory()
                    .<XSString>ensureBuilder(XSString.TYPE_NAME);
            a.attributes().forEach((name, values) -> {
                Attribute attribute = build(Attribute.DEFAULT_ELEMENT_NAME);
                attribute.setName(name);
                attribute.setNameFormat(Attribute.BASIC);
                for (String value : values) {
                    XSString v = strings.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
                    v.setValue(value);
                    attribute.getAttributeValues().add(v);
                }
                statement.getAttributes().add(attribute);
            });
            assertion.getAttributeStatements().add(statement);
        }

        if (signAssertion) {
            sign(assertion, credential);
        }
        response.getAssertions().add(assertion);
        if (signResponse) {
            sign(response, credential);
        }
        return encode(response);
    }

    /** A Response saying the SP's request failed (top-level and second-level status codes, a message). */
    public static String errorResponse(String idpEntityId, String acsUrl, String inResponseTo, String subStatus,
            String message, SamlIdpCredentialService.Credential credential) {
        Response response = build(Response.DEFAULT_ELEMENT_NAME);
        response.setID(newId());
        response.setIssueInstant(Instant.now());
        response.setVersion(SAMLVersion.VERSION_20);
        response.setDestination(acsUrl);
        response.setInResponseTo(inResponseTo);
        response.setIssuer(issuer(idpEntityId));
        response.setStatus(status(StatusCode.RESPONDER, subStatus, message));
        sign(response, credential);
        return encode(response);
    }

    /** A signed LogoutResponse (success) to the SP's single logout URL. */
    public static String logoutResponse(String idpEntityId, String destination, String inResponseTo,
            SamlIdpCredentialService.Credential credential) {
        LogoutResponse response = build(LogoutResponse.DEFAULT_ELEMENT_NAME);
        response.setID(newId());
        response.setIssueInstant(Instant.now());
        response.setVersion(SAMLVersion.VERSION_20);
        response.setDestination(destination);
        response.setInResponseTo(inResponseTo);
        response.setIssuer(issuer(idpEntityId));
        response.setStatus(status(StatusCode.SUCCESS, null, null));
        sign(response, credential);
        return encode(response);
    }

    private static Issuer issuer(String entityId) {
        Issuer issuer = build(Issuer.DEFAULT_ELEMENT_NAME);
        issuer.setFormat(NameIDType.ENTITY);
        issuer.setValue(entityId);
        return issuer;
    }

    private static Status status(String code, String subCode, String message) {
        StatusCode top = build(StatusCode.DEFAULT_ELEMENT_NAME);
        top.setValue(code);
        if (subCode != null) {
            StatusCode second = build(StatusCode.DEFAULT_ELEMENT_NAME);
            second.setValue(subCode);
            top.setStatusCode(second);
        }
        Status status = build(Status.DEFAULT_ELEMENT_NAME);
        status.setStatusCode(top);
        if (message != null) {
            StatusMessage text = build(StatusMessage.DEFAULT_ELEMENT_NAME);
            text.setValue(message);
            status.setStatusMessage(text);
        }
        return status;
    }

    private static void sign(SignableXMLObject object, SamlIdpCredentialService.Credential credential) {
        BasicX509Credential signing = new BasicX509Credential(credential.certificate(), credential.privateKey());
        SignatureSigningParameters parameters = new SignatureSigningParameters();
        parameters.setSigningCredential(signing);
        parameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
        parameters.setSignatureReferenceDigestMethod(SignatureConstants.ALGO_ID_DIGEST_SHA256);
        parameters.setSignatureCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        X509KeyInfoGeneratorFactory keyInfo = new X509KeyInfoGeneratorFactory();
        keyInfo.setEmitEntityCertificate(true);
        parameters.setKeyInfoGenerator(keyInfo.newInstance());
        try {
            SignatureSupport.signObject(object, parameters);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the SAML message", e);
        }
    }

    /** The message as XML text (tests and logging). */
    public static String xml(StatusResponseType message) {
        try {
            return SerializeSupport.nodeToString(XMLObjectSupport.marshall(message));
        } catch (Exception e) {
            throw new IllegalStateException("Could not write the SAML message", e);
        }
    }

    private static String encode(StatusResponseType message) {
        return Base64.getEncoder().encodeToString(xml(message).getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static <T extends XMLObject> T build(QName name) {
        XMLObjectBuilderFactory factory = XMLObjectProviderRegistrySupport.getBuilderFactory();
        return (T) factory.ensureBuilder(name).buildObject(name);
    }
}
