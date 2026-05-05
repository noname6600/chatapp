package com.example.upload.controller;

import com.example.upload.application.PrepareUploadCommand;
import com.example.upload.application.PrepareUploadResult;
import com.example.upload.domain.UploadPurpose;
import com.example.upload.service.UploadSigningService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UploadController.class)
@AutoConfigureMockMvc(addFilters = false)
class UploadControllerPurposeDeserializationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadSigningService uploadSigningService;

    @Test
    void prepare_mapsAvatarAliasToUserAvatarPurpose() throws Exception {
        when(uploadSigningService.prepare(any())).thenReturn(new PrepareUploadResult(
                "user-avatar",
                "https://api.cloudinary.com/v1_1/demo-cloud/auto/upload",
                "https://res.cloudinary.com/demo-cloud/user/avatar/id",
                "user/avatar/id",
                "demo-cloud",
                "demo-key",
                1L,
                "sig",
                "user/avatar",
                1024L,
                List.of("jpg")
        ));

        mockMvc.perform(post("/api/v1/uploads/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"avatar\",\"fileName\":\"a.png\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<PrepareUploadCommand> captor = ArgumentCaptor.forClass(PrepareUploadCommand.class);
        verify(uploadSigningService).prepare(captor.capture());
        assertThat(captor.getValue().purpose()).isEqualTo(UploadPurpose.USER_AVATAR);
    }
}
