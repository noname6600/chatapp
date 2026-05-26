# Upload Service Review

**Service**: File Upload (Port 8088)  
**Responsibility**: Upload, storage, CDN/download  
**Tech Stack**: Spring Boot, Cloudinary (or AWS S3), Postgres  

---

## Critical Issues

| Issue | Severity | Impact | Fix |
|-------|----------|--------|-----|
| Cloudinary config missing | BLOCKER | Service cannot start | Add environment config |
| Path traversal risk | HIGH | Directory escape attack | Validate publicId format |
| File type validation weak | HIGH | Malicious uploads | Strict content-type check |
| No max file size | MEDIUM | Disk exhaustion | Set limit in config |
| Missing virus scanning | LOW | Malware delivery | Note as future |

---

## 🔴 BLOCKER: Cloudinary Bean Not Registered

**Issue**: No CloudinaryConfig class; bean creation fails at runtime

**Current Status**: Missing from `upload-service/src/main/java/com/example/upload/configuration/`

**Fix**:

1. Create `CloudinaryConfig.java`:
```java
@Configuration
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        return new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret));
    }
}
```

2. Add to `application.yaml`:
```yaml
cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME}
  api-key: ${CLOUDINARY_API_KEY}
  api-secret: ${CLOUDINARY_API_SECRET}
```

3. Add environment variables to `docker-compose.yml`:
```yaml
upload-service:
  environment:
    CLOUDINARY_CLOUD_NAME: ${CLOUDINARY_CLOUD_NAME}
    CLOUDINARY_API_KEY: ${CLOUDINARY_API_KEY}
    CLOUDINARY_API_SECRET: ${CLOUDINARY_API_SECRET}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW  

---

## 🔴 HIGH: Path Traversal Vulnerability

**Issue**: File key/path not validated → attacker can escape upload directory

**Example Attack**:
```
POST /api/upload with fileName = "../../../../etc/passwd"
Result: File stored as "../../../../etc/passwd" instead of "uploads/filename"
```

**Fix**: Validate fileName format
```java
public void uploadFile(MultipartFile file) {
    // Generate safe public ID
    String publicId = UUID.randomUUID().toString();
    
    // ❌ Bad - uses user-provided name
    String key = file.getOriginalFilename();
    
    // ✅ Good - uses UUID
    String key = publicId + getFileExtension(file);
    
    // Validate no path traversal
    if (key.contains("..") || key.contains("/") || key.contains("\\")) {
        throw new IllegalArgumentException("Invalid file name");
    }
    
    cloudinary.uploader().upload(file.getBytes(), 
        ObjectUtils.asMap("public_id", publicId));
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW  

---

## 🔴 HIGH: Weak File Type Validation

**Issue**: Attacker uploads exe/sh/bat as image

**Current**: May only check MIME type (spoofable)

**Fix**: Check file signature (magic bytes)
```java
private static final Map<String, byte[]> SIGNATURES = Map.ofEntries(
    Map.entry("image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}),
    Map.entry("image/png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47}),
    Map.entry("image/gif", new byte[]{0x47, 0x49, 0x46})
);

public void validateFileType(MultipartFile file) {
    String contentType = file.getContentType();
    byte[] signature = SIGNATURES.get(contentType);
    
    if (signature == null) {
        throw new IllegalArgumentException("Unsupported file type");
    }
    
    byte[] fileBytes = file.getBytes();
    for (int i = 0; i < signature.length; i++) {
        if (fileBytes[i] != signature[i]) {
            throw new IllegalArgumentException("File signature mismatch");
        }
    }
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW

---

## 🟡 MEDIUM: Max File Size Not Set

**Issue**: No limit → attacker uploads 10GB file → disk full

**Fix**: Set in `application.yaml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW

---

## 🟡 LOW: No Virus Scanning

**Issue**: Uploaded files not scanned for malware

**Note**: DO-NOT-FIX-NOW (future enhancement)

**Recommendation**: Integrate ClamAV or VirusTotal API before file serve

---

## Verification

```bash
./gradlew.bat :upload-service:compileJava --no-daemon

# Set Cloudinary env vars
docker-compose up upload-service -d
docker-compose logs upload-service

# Test upload
curl -X POST http://localhost:8088/api/upload/file \
  -H "Authorization: Bearer <token>" \
  -F "file=@test-image.jpg"
```

---

**Priority**: Fix BLOCKER (Cloudinary) + HIGH (path traversal, file validation)  
**Estimated Effort**: 2-3 hours

**Next**: Read 11-realtime-edge-service-review.md
