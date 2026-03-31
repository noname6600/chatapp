package com.example.upload.controller;

import com.example.common.web.response.ApiResponse;
import com.example.upload.dto.ConfirmUploadRequest;
import com.example.upload.dto.PrepareUploadRequest;
import com.example.upload.dto.PrepareUploadResponse;
import com.example.upload.dto.UploadAssetResponse;
import com.example.upload.service.UploadSigningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return ResponseEntity.ok(ApiResponse.success(uploadSigningService.prepare(request)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<UploadAssetResponse>> confirm(
            @Valid @RequestBody ConfirmUploadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(uploadSigningService.confirm(request)));
    }
}
