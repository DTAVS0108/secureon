package com.secureone.auth.scim;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SCIM 2.0 provisioning", description = "Users and groups provisioned by an identity provider (SCIM bearer token)")
@RestController
@RequestMapping("/scim/v2")
public class ScimServiceProviderConfigController {

    @GetMapping("/ServiceProviderConfig")
    public Map<String, Object> serviceProviderConfig() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
        out.put("patch", Map.of("supported", true));
        out.put("bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0));
        out.put("filter", Map.of("supported", false, "maxResults", 0));
        out.put("authenticationSchemes", List.of(Map.of(
                "type", "oauthbearertoken",
                "name", "OAuth Bearer Token",
                "description", "Platform SCIM bearer token")));
        return out;
    }
}
