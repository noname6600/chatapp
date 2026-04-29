package com.example.common.core.exception;

import org.springframework.http.HttpStatus;

public interface IErrorCode {
    String name();
    HttpStatus getStatus();
}
