# user-avatar-upload-through-upload-service Specification

## Purpose
Define user avatar upload flow contracts that route preparation through upload-service and enforce avatar metadata validation in user-service.

## Requirements

### Requirement: User avatar flow SHALL use upload-service preparation contract
User avatar/profile image uploads SHALL be prepared through upload-service instead of direct Cloudinary signing in user-service.

#### Scenario: Profile UI requests avatar upload preparation
- **WHEN** user selects new avatar image
- **THEN** backend upload preparation request is routed to upload-service with purpose `user-avatar`

#### Scenario: User profile update references normalized asset metadata
- **WHEN** avatar upload succeeds to Cloudinary
- **THEN** profile update payload includes normalized avatar asset metadata from prepared upload flow

### Requirement: User-service SHALL validate and apply avatar metadata
User-service SHALL validate avatar metadata contract and apply accepted metadata to user profile image fields.

#### Scenario: Valid avatar metadata accepted
- **WHEN** profile update request includes valid avatar metadata
- **THEN** user-service updates avatar fields and returns updated profile

#### Scenario: Invalid avatar metadata rejected
- **WHEN** profile update request includes invalid avatar metadata
- **THEN** user-service rejects request with structured validation error
