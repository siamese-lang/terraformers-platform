import { localGcpBrowserAuthClient as client } from './localGcpBrowserAuthClient';

function token(overrides = {}) {
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: 'https://identity.example.test/case-c',
    client_id: 'case-c-runtime-client',
    token_use: 'access',
    sub: 'gcp-preview-owner',
    email: 'owner@example.test',
    nickname: 'Developer',
    exp: now + 600,
    ...overrides,
  };
  const part = (value) => btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  return part({ alg: 'RS256', kid: 'fixture' }) + '.' + part(payload) + '.signature';
}

beforeEach(async () => {
  await client.signOut();
});

test('keeps an operator-supplied short-lived access token in memory only', async () => {
  const issued = token();
  await client.signIn({ token: issued });
  expect(await client.getToken('access')).toBe(issued);
  expect(await client.getToken('id')).toBeNull();
  expect(await client.getCurrentUser()).toEqual({
    userId: 'gcp-preview-owner',
    username: 'owner@example.test',
    email: 'owner@example.test',
    nickname: 'Developer',
  });
  await client.signOut();
  expect(await client.getToken('access')).toBeNull();
  expect(await client.getCurrentUser()).toBeNull();
});

test.each([
  ['empty', ''],
  ['malformed', 'not-a-jwt'],
  ['wrong issuer', token({ iss: 'https://untrusted.example' })],
  ['wrong audience', token({ client_id: 'different-client' })],
  ['identity token', token({ token_use: 'id' })],
  ['expired token', token({ exp: 1 })],
  ['missing subject', token({ sub: '' })],
])('rejects %s token before opening local preview', async (_, given) => {
  await expect(client.signIn({ token: given })).rejects.toThrow('Invalid or expired');
  expect(await client.getToken('access')).toBeNull();
});

test('a previously accepted preview token is discarded when it expires', async () => {
  const now = Math.floor(Date.now() / 1000);
  await client.signIn({ token: token({ exp: now + 300 }) });
  const originalNow = Date.now;
  try {
    Date.now = () => (now + 400) * 1000;
    expect(await client.getToken('access')).toBeNull();
    expect(await client.getCurrentUser()).toBeNull();
  } finally {
    Date.now = originalNow;
  }
});
