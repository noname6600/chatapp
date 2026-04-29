package com.example.upload.service;

import com.cloudinary.Cloudinary;
import com.example.common.core.exception.BusinessException;
import com.example.upload.config.UploadPolicyProperties;
import com.example.upload.domain.UploadPurpose;
import com.example.upload.dto.ConfirmUploadRequest;
import com.example.upload.dto.PrepareUploadRequest;
import com.example.upload.dto.PrepareUploadResponse;
import com.example.upload.dto.UploadAssetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

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
        PrepareUploadRequest request = new PrepareUploadRequest();
        request.setPurpose(UploadPurpose.CHAT_ATTACHMENT);
        request.setFileName("a.png");

        PrepareUploadResponse response = service.prepare(request);

        assertThat(response.getPurpose()).isEqualTo("chat-attachment");
        assertThat(response.getSignature()).isEqualTo("sig-123");
        assertThat(response.getFolder()).isEqualTo("chat/attachments");
        assertThat(response.getMaxBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(response.getAllowedFormats()).contains("jpg", "png", "pdf");
        assertThat(response.getPublicId()).startsWith("chat/attachments/");
        assertThat(response.getUploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/demo-cloud/auto/upload");
    }

    @Test
    void confirm_rejectsPolicyViolationForFormat() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setPurpose(UploadPurpose.USER_AVATAR);
        request.setPublicId("user/avatar/abc");
        request.setSecureUrl("https://res.cloudinary.com/demo-cloud/image/upload/v1/user/avatar/abc.png");
        request.setResourceType("image");
        request.setFormat("gif");
        request.setBytes(100L);
        request.setWidth(100);
        request.setHeight(100);

        assertThatThrownBy(() -> service.confirm(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("format is not allowed");
    }

    @Test
    void confirm_acceptsValidPayloadAndNormalizesContract() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setPurpose(UploadPurpose.CHAT_ATTACHMENT);
        request.setPublicId("chat/attachments/xyz");
        request.setSecureUrl("https://res.cloudinary.com/demo-cloud/image/upload/v1/chat/attachments/xyz.jpg");
        request.setResourceType("IMAGE");
        request.setFormat("JPG");
        request.setBytes(1024L);
        request.setWidth(640);
        request.setHeight(480);

        UploadAssetResponse response = service.confirm(request);

        assertThat(response.getPublicId()).isEqualTo("chat/attachments/xyz");
        assertThat(response.getResourceType()).isEqualTo("image");
        assertThat(response.getFormat()).isEqualTo("jpg");
        assertThat(response.getBytes()).isEqualTo(1024L);
        assertThat(response.getWidth()).isEqualTo(640);
        assertThat(response.getHeight()).isEqualTo(480);
    }
}

