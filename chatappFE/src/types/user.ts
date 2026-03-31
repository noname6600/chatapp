export interface UserProfile {
  accountId: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  aboutMe: string | null;
  backgroundColor: string | null;
}

export interface UpdateProfileRequest {
  username?: string;
  displayName?: string;
  aboutMe?: string;
  backgroundColor?: string;
}