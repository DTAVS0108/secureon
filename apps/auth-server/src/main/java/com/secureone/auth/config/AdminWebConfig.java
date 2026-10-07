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
    }
}
