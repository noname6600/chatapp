# upload-service-cloudinary-core Specification

## Purpose
Define secure, purpose-aware Cloudinary upload preparation and normalized metadata rules provided by upload-service.

## Requirements

### Requirement: Upload service SHALL provide secure Cloudinary upload preparation
The upload-service SHALL generate secure upload parameters for Cloudinary uploads using server-side signing and purpose-based constraints.

#### Scenario: Prepare upload for chat attachment
- **WHEN** client requests upload preparation with purpose `chat-attachment`
- **THEN** service returns signed upload parameters including signature, timestamp, folder/public-id policy, and allowed file constraints for chat usage

#### Scenario: Prepare upload for user avatar
- **WHEN** client requests upload preparation with purpose `user-avatar`
- **THEN** service returns signed upload parameters including signature, timestamp, folder/public-id policy, and allowed file constraints for avatar usage

#### Scenario: Reject unknown purpose
- **WHEN** client requests upload preparation with unsupported purpose value
- **THEN** service rejects request with validation error and does not emit signing data

### Requirement: Upload service SHALL enforce purpose-based policy
The upload-service SHALL enforce policy matrix per purpose, including folder pathing, format allow-list, and size limits.

#### Scenario: Policy applies folder convention
- **WHEN** upload preparation is generated for each purpose
- **THEN** returned folder path follows configured purpose folder convention

#### Scenario: Policy returns size and format constraints
- **WHEN** upload preparation is generated
- **THEN** payload includes max-size and allowed-format constraints expected by consuming clients

### Requirement: Upload service SHALL expose normalized metadata contract
The upload-service SHALL provide a normalized metadata contract for consumers to submit/confirm uploaded assets.

#### Scenario: Metadata contract includes required Cloudinary fields
- **WHEN** client submits uploaded asset metadata
- **THEN** contract requires normalized fields including publicId, secureUrl, resourceType, format, width/height (when applicable), and bytes

#### Scenario: Invalid metadata is rejected
- **WHEN** required metadata fields are missing or malformed
- **THEN** service rejects metadata confirmation with structured validation errors
