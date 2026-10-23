import test from 'node:test';
import assert from 'node:assert/strict';
import { tokenizeProto } from '../src/protoSyntax.js';

test('proto tokens distinguish syntax without changing source text', () => {
  const source = 'syntax = "proto3";\nmessage Player {\n  repeated string names = 1;\n  map<string, demo.Reward> items = 0x10;\n}\n';
  const lines = tokenizeProto(source);
  assert.equal(lines.map(line => line.map(token => token.text).join('')).join('\n'), source);
  const tokens = lines.flat();
  assert.ok(tokens.some(token => token.kind === 'keyword' && token.text === 'message'));
  assert.ok(tokens.some(token => token.kind === 'type' && token.text === 'string'));
  assert.ok(tokens.some(token => token.kind === 'type' && token.text === 'demo.Reward'));
  assert.ok(tokens.some(token => token.kind === 'string' && token.text === '"proto3"'));
  assert.ok(tokens.some(token => token.kind === 'number' && token.text === '0x10'));
});

test('comments, escaped strings and CRLF preserve their ranges and content', () => {
  const source = '/* message Fake {\r\n string ignored = 9; } */\r\noption note = "escaped \\" // still a string"; // real comment\r\n';
  const lines = tokenizeProto(source);
  assert.equal(lines.map(line => line.map(token => token.text).join('')).join('\n'), source);
  assert.ok(lines[1].filter(token => token.text.trim()).every(token => token.kind === 'comment'));
  assert.ok(lines[2].some(token => token.kind === 'string' && token.text.includes('// still a string')));
  assert.ok(lines[2].some(token => token.kind === 'comment' && token.text === '// real comment\r'));
  for (const line of lines) for (const token of line) assert.equal(line.map(t => t.text).join('').slice(token.start, token.end), token.text);
});

test('incomplete comments and HTML-like strings remain literal text', () => {
  for (const source of ['', '/* unfinished\nmessage StillComment', 'string html = "<script>alert(1)</script>";', 'string broken = "unfinished']) {
    assert.equal(tokenizeProto(source).map(line => line.map(token => token.text).join('')).join('\n'), source);
  }
});
