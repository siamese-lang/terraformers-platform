import { cognitoAmplifyAuthClient } from './providers/cognitoAmplifyAuthClient';

// Application-facing authentication boundary. Provider-specific configuration,
// errors, user attributes, and token objects are normalized by the adapter.
export const initializeAuth = () => cognitoAmplifyAuthClient.initialize();
export const getCurrentUser = () => cognitoAmplifyAuthClient.getCurrentUser();
export const getToken = (tokenType = 'access') => cognitoAmplifyAuthClient.getToken(tokenType);
export const signIn = (credentials) => cognitoAmplifyAuthClient.signIn(credentials);
export const signUp = (account) => cognitoAmplifyAuthClient.signUp(account);
export const confirmSignUp = (confirmation) => cognitoAmplifyAuthClient.confirmSignUp(confirmation);
export const requestPasswordReset = (username) => cognitoAmplifyAuthClient.requestPasswordReset(username);
export const signOut = () => cognitoAmplifyAuthClient.signOut();
