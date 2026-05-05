package com.example.upload.service;

import com.cloudinary.Cloudinary;
import com.example.common.core.exception.BusinessException;
import com.example.upload.application.ConfirmUploadCommand;
import com.example.upload.application.ConfirmUploadResult;
import com.example.upload.application.PrepareUploadCommand;
import com.example.upload.application.PrepareUploadResult;
import com.example.upload.config.UploadPolicyProperties;
import com.example.upload.domain.UploadPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadSigningServiceTest {

    private UploadSigningService service;

    @BeforeEach
    void setUp() {
        UploadPolicyProperties props = new UploadPolicyProperties();

        UploadPolicyProperties.Purpose chat = new UploadPolicyProperties.Purpose();
        chat.setFolder("chat/attachments");
        chat.setMaxBytes(10L * 1024 * 1024);
        chat.setAllowedFormats(List.of("jpg", "png", "pdf"));
        chat.setAllowedResourceTypes(List.of("image", "raw"));
        props.setChatAttachment(chat);

        UploadPolicyProperties.Purpose avatar = new UploadPolicyProperties.Purpose();
        avatar.setFolder("user/avatar");
        avatar.setMaxBytes(5L * 1024 * 1024);
        avatar.setAllowedFormats(List.of("jpg", "png", "webp"));
        avatar.setAllowedResourceTypes(List.of("image"));
        props.setUserAvatar(avatar);

        UploadPolicyRegistry registry = new UploadPolicyRegistry(props);
        registry.init();

        Cloudinary cloudinary = mock(Cloudinary.class);
        when(cloudinary.apiSignRequest(anyMap(), eq("secret"))).thenReturn("sig-123");

        service = new UploadSigningService(cloudinary, registry);
        ReflectionTestUtils.setField(service, "cloudName", "demo-cloud");
        ReflectionTestUtils.setField(service, "apiKey", "demo-key");
        ReflectionTestUtils.setField(service, "apiSecret", "secret");
    }

    @Test
    void prepare_returnsSignedPayloadWithPolicyConstraints() {
        PrepareUploadCommand command = new PrepareUploadCommand(
                "user-1",
                UploadPurpose.CHAT_ATTACHMENT,
                "a.png",
                1024L
        );

        PrepareUploadResult response = service.prepare(command);

        assertThat(response.purpose()).isEqualTo("chat-attachment");
        assertThat(response.signature()).isEqualTo("sig-123");
        assertThat(response.folder()).isEqualTo("chat/attachments");
        assertThat(response.maxBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(response.allowedFormats()).contains("jpg", "png", "pdf");
        assertThat(response.assetKey()).startsWith("chat/attachments/");
        assertThat(response.uploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/demo-cloud/auto/upload");
    }

    @Test
    void confirm_rejectsPolicyViolationForFormat() {
        ConfirmUploadCommand command = new ConfirmUploadCommand(
            "user-1",
            "user/avatar/abc",
            "https://res.cloudinary.com/demo-cloud/image/upload/v1/user/avatar/abc.png",
            UploadPurpose.USER_AVATAR,
            "image",
            "gif",
            100L,
            100,
            100,
            null,
            null
        );

        assertThatThrownBy(() -> service.confirm(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("format is not allowed");
    }

    @Test
    void confirm_acceptsValidPayloadAndNormalizesContract() {
        ConfirmUploadCommand command = new ConfirmUploadCommand(
                "user-1",
                "chat/attachments/xyz",
                "https://res.cloudinary.com/demo-cloud/image/upload/v1/chat/attachments/xyz.jpg",
                UploadPurpose.CHAT_ATTACHMENT,
                "IMAGE",
                "JPG",
                1024L,
                640,
                480,
                null,
                null
        );

        ConfirmUploadResult response = service.confirm(command);

        assertThat(response.metadata().getPublicId()).isEqualTo("chat/attachments/xyz");
        assertThat(response.metadata().getResourceType()).isEqualTo("image");
        assertThat(response.metadata().getFormat()).isEqualTo("jpg");
        assertThat(response.metadata().getBytes()).isEqualTo(1024L);
        assertThat(response.metadata().getWidth()).isEqualTo(640);
        assertThat(response.metadata().getHeight()).isEqualTo(480);
    }
}

