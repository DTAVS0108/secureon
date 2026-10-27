package com.secureone.auth.config;

import com.secureone.auth.admin.AdminAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAccessInterceptor adminAccessInterceptor;
    private final OperatorTenantScopeInterceptor operatorTenantScope;

    public AdminWebConfig(
            AdminAccessInterceptor adminAccessInterceptor, OperatorTenantScopeInterceptor operatorTenantScope) {
        this.adminAccessInterceptor = adminAccessInterceptor;
        this.operatorTenantScope = operatorTenantScope;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Scope first, so the access checks below already run inside the operator's tenant.
        registry.addInterceptor(operatorTenantScope).addPathPatterns("/**");
        registry.addInterceptor(adminAccessInterceptor).addPathPatterns("/api/admin/v1/**");
        registry.addInterceptor(settingsAudit).addPathPatterns("/api/admin/v1/settings/**",
                "/api/admin/v1/applications/*/settings/**");
        registry.addInterceptor(impersonationGuard).addPathPatterns("/api/v1/account/**");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.authn.ImpersonationGuard impersonationGuard;

    @org.springframework.beans.factory.annotation.Autowired
    private com.secureone.auth.audit.SettingsChangeAudit.Recorder settingsAudit;
}
