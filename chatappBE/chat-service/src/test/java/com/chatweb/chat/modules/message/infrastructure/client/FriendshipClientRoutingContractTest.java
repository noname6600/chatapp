package com.chatweb.chat.modules.message.infrastructure.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FriendshipClientRoutingContractTest {

    @Test
    void friendshipClient_mapping_targets_internal_blocked_between_endpoint() throws NoSuchMethodException {
        Method method = FriendshipClient.class.getMethod("isBlockedBetween", UUID.class, UUID.class);
        GetMapping getMapping = method.getAnnotation(GetMapping.class);

        assertThat(getMapping).isNotNull();
        assertThat(getMapping.value()).containsExactly("/api/v1/internal/friends/blocked-between");
    }

    @Test
    void local_profile_defaults_friendship_service_url_to_8085() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-local.yaml"));

        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("services.friendship.url"))
                .isEqualTo("${SERVICES_FRIENDSHIP_URL:http://localhost:8085}")
                .doesNotContain(":8083");
    }
}
