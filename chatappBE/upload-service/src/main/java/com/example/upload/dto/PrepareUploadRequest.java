package com.example.upload.dto;

import com.example.upload.domain.UploadPurpose;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrepareUploadRequest {

    @NotNull(message = "purpose is required")
    @JsonDeserialize(using = UploadPurposeDeserializer.class)
    private UploadPurpose purpose;

    private String fileName;
}
