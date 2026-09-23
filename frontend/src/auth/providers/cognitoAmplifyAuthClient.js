import { Amplify } from 'aws-amplify';
import {
  confirmSignUp as amplifyConfirmSignUp,
  fetchAuthSession,
  fetchUserAttributes,
  getCurrentUser as amplifyGetCurrentUser,
  resetPassword,
  signIn as amplifySignIn,
  signOut as amplifySignOut,
  signUp as amplifySignUp,
} from 'aws-amplify/auth';
import { buildAmplifyConfig, logCognitoConfigSummary } from '../../awsConfig';

const EXPECTED_GUEST_ERRORS = new Set([
  'UserUnAuthenticatedException',
  'NotAuthorizedException',
]);

const asTokenString = (token) => token?.toString() || null;

export const cognitoAmplifyAuthClient = {
  initialize() {
    const config = buildAmplifyConfig();
    logCognitoConfigSummary(config, process.env.NODE_ENV);
    if (config) {
      Amplify.configure(config);
    }
  },

  async getCurrentUser() {
    try {
      const currentUser = await amplifyGetCurrentUser();
      const attributes = await fetchUserAttributes();
      return {
        userId: currentUser.userId,
        username: currentUser.username,
        email: attributes.email || '',
        nickname: attributes.nickname || '',
      };
    } catch (error) {
      if (EXPECTED_GUEST_ERRORS.has(error?.name)) {
        return null;
      }
      throw error;
    }
  },

  async getToken(tokenType = 'access') {
    const session = await fetchAuthSession();
    if (tokenType === 'access') {
      return asTokenString(session.tokens?.accessToken);
    }
    if (tokenType === 'id') {
      return asTokenString(session.tokens?.idToken);
    }
    return null;
  },

  signIn({ username, password }) {
    return amplifySignIn({ username, password });
  },

  signUp({ email, password, nickname }) {
    return amplifySignUp({
      username: email,
      password,
      options: { userAttributes: { email, nickname } },
    });
  },

  confirmSignUp({ username, confirmationCode }) {
    return amplifyConfirmSignUp({ username, confirmationCode });
  },

  requestPasswordReset(username) {
    return resetPassword({ username });
  },

  signOut() {
    return amplifySignOut();
  },
};
