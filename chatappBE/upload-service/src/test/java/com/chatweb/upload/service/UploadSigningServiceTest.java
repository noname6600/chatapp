package com.chatweb.upload.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.upload.application.ConfirmUploadCommand;
import com.chatweb.upload.application.ConfirmUploadResult;
import com.chatweb.upload.application.PrepareUploadCommand;
import com.chatweb.upload.application.PrepareUploadResult;
import com.chatweb.upload.config.UploadPolicyProperties;
import com.chatweb.upload.domain.UploadPurpose;
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
    private com.cloudinary.Api cloudinaryApi;

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

        UploadPolicyProperties.Purpose roomAvatar = new UploadPolicyProperties.Purpose();
        roomAvatar.setFolder("room_avatars");
        roomAvatar.setMaxBytes(5L * 1024 * 1024);
        roomAvatar.setAllowedFormats(List.of("jpg", "png", "webp"));
        roomAvatar.setAllowedResourceTypes(List.of("image"));
        props.setRoomAvatar(roomAvatar);

        UploadPolicyRegistry registry = new UploadPolicyRegistry(props);
        registry.init();

        Cloudinary cloudinary = mock(Cloudinary.class);
        cloudinaryApi = mock(com.cloudinary.Api.class);
        when(cloudinary.apiSignRequest(anyMap(), eq("secret"))).thenReturn("sig-123");
        when(cloudinary.api()).thenReturn(cloudinaryApi);

        service = new UploadSigningService(cloudinary, registry);
        ReflectionTestUtils.setField(service, "cloudName", "demo-cloud");
        ReflectionTestUtils.setField(service, "apiKey", "demo-key");
        ReflectionTestUtils.setField(service, "apiSecret", "secret");
        ReflectionTestUtils.setField(service, "prepareTokenSecret", "prepare-secret");
        ReflectionTestUtils.setField(service, "requirePrepareToken", true);
        ReflectionTestUtils.setField(service, "prepareTokenTtlSeconds", 600L);
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
        assertThat(response.prepareToken()).isNotBlank();
        assertThat(response.uploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/demo-cloud/auto/upload");
    }

    @Test
        void confirm_rejectsWhenPrepareTokenMissing() {
        ConfirmUploadCommand command = new ConfirmUploadCommand(
            "user-1",
            "user/avatar/abc",
            null,
            "https://res.cloudinary.com/demo-cloud/image/upload/v1/user/avatar/abc.png",
            UploadPurpose.USER_AVATAR,
            "image",
            "png",
            100L,
            100,
            100,
            null,
            null
        );

        assertThatThrownBy(() -> service.confirm(command))
                .isInstanceOf(BusinessException.class)
            .hasMessageContaining("prepareToken is required");
    }

    @Test
        void confirm_acceptsValidPayloadAndUsesServerVerifiedMetadata() throws Exception {
        PrepareUploadResult prepared = service.prepare(new PrepareUploadCommand(
                "user-1",
            UploadPurpose.CHAT_ATTACHMENT,
            "a.jpg",
            100L
        ));

        ApiResponse verified = mock(ApiResponse.class);
        when(verified.isEmpty()).thenReturn(false);
        when(verified.get("public_id")).thenReturn(prepared.assetKey());
        when(verified.get("secure_url")).thenReturn("https://res.cloudinary.com/demo-cloud/image/upload/v1/" + prepared.assetKey() + ".jpg");
        when(verified.get("resource_type")).thenReturn("image");
        when(verified.get("format")).thenReturn("jpg");
        when(verified.get("bytes")).thenReturn(1024L);
        when(verified.get("width")).thenReturn(640);
        when(verified.get("height")).thenReturn(480);
        when(verified.get("duration")).thenReturn(null);
        when(verified.get("original_filename")).thenReturn("from-cloudinary");
        when(cloudinaryApi.resource(eq(prepared.assetKey()), anyMap())).thenReturn(verified);

        ConfirmUploadCommand command = new ConfirmUploadCommand(
            "user-1",
            prepared.assetKey(),
            prepared.prepareToken(),
            "https://res.cloudinary.com/demo-cloud/image/upload/v1/forged.jpg",
            UploadPurpose.CHAT_ATTACHMENT,
            "raw",
            "pdf",
            9999999L,
            null,
            null,
            null,
            "client-name"
        );

        ConfirmUploadResult response = service.confirm(command);

        assertThat(response.metadata().getPublicId()).isEqualTo(prepared.assetKey());
        assertThat(response.metadata().getSecureUrl()).isEqualTo("https://res.cloudinary.com/demo-cloud/image/upload/v1/" + prepared.assetKey() + ".jpg");
        assertThat(response.metadata().getResourceType()).isEqualTo("image");
        assertThat(response.metadata().getFormat()).isEqualTo("jpg");
        assertThat(response.metadata().getBytes()).isEqualTo(1024L);
        assertThat(response.metadata().getWidth()).isEqualTo(640);
        assertThat(response.metadata().getHeight()).isEqualTo(480);
        assertThat(response.metadata().getOriginalFilename()).isEqualTo("from-cloudinary");
        }

        @Test
        void confirm_rejectsWhenVerifiedFormatViolatesPolicy() throws Exception {
        PrepareUploadResult prepared = service.prepare(new PrepareUploadCommand(
            "user-1",
            UploadPurpose.USER_AVATAR,
            "a.png",
            100L
        ));

        ApiResponse verified = mock(ApiResponse.class);
        when(verified.isEmpty()).thenReturn(false);
        when(verified.get("public_id")).thenReturn(prepared.assetKey());
        when(verified.get("secure_url")).thenReturn("https://res.cloudinary.com/demo-cloud/image/upload/v1/" + prepared.assetKey() + ".gif");
        when(verified.get("resource_type")).thenReturn("image");
        when(verified.get("format")).thenReturn("gif");
        when(verified.get("bytes")).thenReturn(1024L);
        when(verified.get("width")).thenReturn(200);
        when(verified.get("height")).thenReturn(100);
        when(cloudinaryApi.resource(eq(prepared.assetKey()), anyMap())).thenReturn(verified);

        ConfirmUploadCommand command = new ConfirmUploadCommand(
            "user-1",
            prepared.assetKey(),
            prepared.prepareToken(),
            "https://res.cloudinary.com/demo-cloud/image/upload/v1/" + prepared.assetKey() + ".gif",
            UploadPurpose.USER_AVATAR,
            "image",
            "gif",
            1024L,
            200,
            100,
            null,
            null
        );

        assertThatThrownBy(() -> service.confirm(command))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("format is not allowed");
    }
}

