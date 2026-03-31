export type UploadPurpose = "chat-attachment" | "user-avatar";

export interface UploadPrepareResponse {
  purpose: UploadPurpose;
  cloudName: string;
  apiKey: string;
  uploadUrl: string;
  timestamp: number;
  signature: string;
  folder: string;
  publicId: string;
  maxBytes: number;
  allowedFormats: string[];
}

export interface UploadAssetMetadata {
  purpose: UploadPurpose;
  publicId: string;
  secureUrl: string;
  resourceType: string;
  format: string;
  bytes: number;
  width?: number;
  height?: number;
  duration?: number;
  originalFilename?: string;
}

export interface CloudinaryUploadResult {
  public_id: string;
  secure_url: string;
  resource_type: string;
  format: string;
  bytes: number;
  width?: number;
  height?: number;
  duration?: number;
  original_filename?: string;
}
