const { test } = require('node:test');
const assert = require('node:assert/strict');
const { signToken, verifyToken } = require('../src/middleware/auth');

test('signToken/verifyToken roundtrip returns the same userId', () => {
  const token = signToken('user-123');
  assert.ok(typeof token === 'string' && token.length > 10);
  const payload = verifyToken(token);
  assert.strictEqual(payload.userId, 'user-123');
});

test('verifyToken rejects a malformed token', () => {
  assert.throws(() => verifyToken('not-a-valid-jwt'));
});

test('verifyToken rejects a token signed with the wrong secret', () => {
  const jwt = require('jsonwebtoken');
  const forged = jwt.sign({ userId: 'hacker' }, 'wrong-secret', { expiresIn: '1h' });
  assert.throws(() => verifyToken(forged));
});
