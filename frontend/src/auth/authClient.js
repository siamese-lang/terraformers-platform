import { cognitoAmplifyAuthClient } from './providers/cognitoAmplifyAuthClient';
import { localGcpBrowserAuthClient } from './providers/localGcpBrowserAuthClient';

// The local GCP preview can never be enabled in a production build.
export const isGcpBrowserPreview = process.env.NODE_ENV === 'development'
  && process.env.REACT_APP_GCP_BROWSER_PREVIEW === 'true';
const authClient = isGcpBrowserPreview ? localGcpBrowserAuthClient : cognitoAmplifyAuthClient;

// Application-facing authentication boundary.
export const initializeAuth = () => authClient.initialize();
export const getCurrentUser = () => authClient.getCurrentUser();
export const getToken = (tokenType = 'access') => authClient.getToken(tokenType);
export const signIn = (credentials) => authClient.signIn(credentials);
export const signUp = (account) => authClient.signUp(account);
export const confirmSignUp = (confirmation) => authClient.confirmSignUp(confirmation);
export const requestPasswordReset = (username) => authClient.requestPasswordReset(username);
export const signOut = () => authClient.signOut();
