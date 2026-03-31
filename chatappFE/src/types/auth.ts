export interface TokenPair {
  accessToken: string
  refreshToken: string
  accessTokenExpiresIn: number
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
}