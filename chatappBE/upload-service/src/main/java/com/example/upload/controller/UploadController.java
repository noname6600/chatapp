package com.example.upload.controller;

import com.example.common.web.response.ApiResponse;
import com.example.upload.application.ConfirmUploadCommand;
import com.example.upload.application.ConfirmUploadResult;
import com.example.upload.application.PrepareUploadCommand;
import com.example.upload.application.PrepareUploadResult;
import com.example.upload.dto.ConfirmUploadRequest;
import com.example.upload.dto.PrepareUploadRequest;
import com.example.upload.dto.PrepareUploadResponse;
import com.example.upload.dto.UploadAssetResponse;
import com.example.upload.service.UploadSigningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadSigningService uploadSigningService;

    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<PrepareUploadResponse>> prepare(
            @Valid @RequestBody PrepareUploadRequest request
    ) {
        PrepareUploadCommand command = new PrepareUploadCommand(
            currentUserId(),
            request.getPurpose(),
            request.getFileName(),
            0L
        );
        PrepareUploadResult result = uploadSigningService.prepare(command);

        PrepareUploadResponse response = PrepareUploadResponse.builder()
            .purpose(result.purpose())
            .cloudName(result.cloudName())
            .apiKey(result.apiKey())
            .uploadUrl(result.uploadUrl())
            .timestamp(result.timestamp())
            .signature(result.signature())
            .folder(result.folder())
            .publicId(result.assetKey())
            .maxBytes(result.maxBytes())
            .allowedFormats(result.allowedFormats())
            .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<UploadAssetResponse>> confirm(
            @Valid @RequestBody ConfirmUploadRequest request
    ) {
        ConfirmUploadCommand command = new ConfirmUploadCommand(
                currentUserId(),
                request.getPublicId(),
                request.getSecureUrl(),
                request.getPurpose(),
                request.getResourceType(),
                request.getFormat(),
                request.getBytes(),
                request.getWidth(),
                request.getHeight(),
                request.getDuration(),
                request.getOriginalFilename()
        );
        ConfirmUploadResult result = uploadSigningService.confirm(command);

        UploadAssetResponse response = UploadAssetResponse.builder()
                .purpose(request.getPurpose().value())
                .publicId(result.metadata().getPublicId())
                .secureUrl(result.metadata().getSecureUrl())
                .resourceType(result.metadata().getResourceType())
                .format(result.metadata().getFormat())
                .bytes(result.metadata().getBytes())
                .width(result.metadata().getWidth())
                .height(result.metadata().getHeight())
                .duration(result.metadata().getDuration())
                .originalFilename(result.metadata().getOriginalFilename())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getName();
    }
}
