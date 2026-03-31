package com.example.upload.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "upload.policy")
public class UploadPolicyProperties {

    private Purpose chatAttachment = new Purpose();
    private Purpose userAvatar = new Purpose();

    @Getter
    @Setter
    public static class Purpose {
        private String folder;
        private Long maxBytes;
        private List<String> allowedFormats = List.of();
        private List<String> allowedResourceTypes = List.of();
    }
}
