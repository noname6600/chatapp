package com.example.common.web.exception;

import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.IErrorCode;
import com.example.common.web.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private enum TestDomainErrorCode implements IErrorCode {
        SAMPLE(HttpStatus.CONFLICT);

        private final HttpStatus status;

        TestDomainErrorCode(HttpStatus status) {
            this.status = status;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }
    }

    @Test
    void handleBusiness_usesGenericErrorContract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BusinessException ex = new BusinessException(TestDomainErrorCode.SAMPLE, "sample-message");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SAMPLE", response.getBody().getError().getCode());
        assertEquals("sample-message", response.getBody().getError().getMessage());
    }
}
