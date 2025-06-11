package com.example.azureresourcelisting.Config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.management.AzureEnvironment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Configuration
public class AzureConfig {
@Value("${azure.username}")
    private String username;

    @Value("${azure.password}")
    private String password;

    @Value("${azure.tenant-id}")
    private String tenantId;

    @Value("${azure.subscription-id}")
    private String subscriptionId;

    @Bean
    public AzureResourceManager azureResourceManager() {
           DeviceCodeCredential credential = new DeviceCodeCredentialBuilder()
        .challengeConsumer(challenge -> System.out.println(challenge.getMessage()))
        .tenantId(tenantId)
        .build();

    AzureProfile profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

    return AzureResourceManager
            .authenticate(credential, profile)
            .withSubscription(subscriptionId);
    }
@Bean
public AzureResourceManager azureResourceManagerWithDefaultCredential() {
    AzureProfile profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
    return AzureResourceManager
            .authenticate(new DefaultAzureCredentialBuilder().build(), profile)
            .withSubscription(subscriptionId);
}

}