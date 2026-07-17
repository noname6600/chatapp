package com.chatweb.upload;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "CLOUDINARY_CLOUD_NAME=test-cloud",
    "CLOUDINARY_API_KEY=test-key",
    "CLOUDINARY_API_SECRET=test-secret"
})
class UploadServiceApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
