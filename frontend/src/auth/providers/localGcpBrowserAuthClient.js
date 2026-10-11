/**
 * Development-only GCP browser preview adapter. This is NOT an identity provider.
 * JWT signatures, issuer, audience and authorization remain enforced by the backend.
 * Tokens are held only in module memory and are never persisted or logged.
 */
const EXPECTED_ISSUER = 'https://identity.example.test/case-c';
const EXPECTED_CLIENT = 'case-c-runtime-client';
let accessToken = null;

function decodedClaims(token) {
  if (typeof token !== 'string' || token.length > 8192) return null;
  const parts = token.trim().split('.');
  if (parts.length !== 3 || parts.some((part) => !part)) return null;
  try {
    const urlBase64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(urlBase64.padEnd(Math.ceil(urlBase64.length / 4) * 4, '=')));
    const now = Math.floor(Date.now() / 1000);
    if (payload.iss !== EXPECTED_ISSUER || payload.client_id !== EXPECTED_CLIENT ||
        payload.token_use !== 'access' || typeof payload.sub !== 'string' ||
        !payload.sub || !Number.isSafeInteger(payload.exp) || payload.exp <= now + 15) {
      return null;
    }
    return payload;
  } catch (_) {
    return null;
  }
}

function currentClaims() {
  const claims = decodedClaims(accessToken);
  if (!claims) accessToken = null;
  return claims;
}

export const localGcpBrowserAuthClient = {
  initialize() {},
  async getCurrentUser() {
    const claims = currentClaims();
    return claims ? {
      userId: claims.sub,
      username: claims.username || claims.email || claims.sub,
      email: typeof claims.email === 'string' ? claims.email : '',
      nickname: typeof claims.nickname === 'string' ? claims.nickname : '',
    } : null;
  },
  async getToken(tokenType = 'access') {
    return tokenType === 'access' && currentClaims() ? accessToken : null;
  },
  async signIn({ token } = {}) {
    if (!decodedClaims(token)) {
      throw new Error('Invalid or expired local preview JWT');
    }
    accessToken = token.trim();
  },
  async signOut() {
    accessToken = null;
  },
  async signUp() {
    throw new Error('Sign-up is unavailable in local GCP preview');
  },
  async confirmSignUp() {
    throw new Error('Confirmation is unavailable in local GCP preview');
  },
  async requestPasswordReset() {
    throw new Error('Password reset is unavailable in local GCP preview');
  },
};
