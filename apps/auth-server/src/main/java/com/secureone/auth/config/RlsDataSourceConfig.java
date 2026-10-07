package com.secureone.auth.config;

import com.secureone.auth.tenant.RlsScopedDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wraps the application DataSource so every connection carries the request's RLS scope. */
@Configuration
public class RlsDataSourceConfig {

    @Bean
    static BeanPostProcessor rlsScopedDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource ds && !(bean instanceof RlsScopedDataSource) && "dataSource".equals(beanName)) {
                    return new RlsScopedDataSource(ds);
                }
                return bean;
            }
        };
    }
}
