import fs from 'fs';
import path from 'path';

const bridgeSource = fs.readFileSync(
  path.resolve(
    __dirname,
    '..',
    'android',
    'app',
    'src',
    'main',
    'java',
    'com',
    'clipcascade',
    'AsyncStorageBridge.kt',
  ),
  'utf8',
);

describe('native AsyncStorage bridge ownership', () => {
  test('bridge release never closes the process-wide AsyncStorage database', () => {
    expect(bridgeSource).toContain(
      'ReactDatabaseSupplier.getInstance(applicationContext).get()',
    );
    expect(bridgeSource).toMatch(/fun disconnect\(\) \{\s*db = null\s*\}/);
    expect(bridgeSource).not.toContain('db?.close()');
    expect(bridgeSource).not.toContain('database.close()');
  });

  test('values preserve the JavaScript JSON-string representation', () => {
    expect(bridgeSource).toContain('JSONObject.quote(value)');
    expect(bridgeSource).toContain('JSONTokener(rawValue).nextValue()');
    expect(bridgeSource).toContain('is String -> decoded');
    expect(bridgeSource).not.toContain('.replace("^\\\"|\\\"$".toRegex(), "")');
    expect(bridgeSource).not.toContain('put(VALUE_COLUMN, "\\\"$value\\\"")');
  });

  test('writes fail closed when the shared database is unavailable', () => {
    expect(bridgeSource).toContain('if (database?.isOpen != true)');
    expect(bridgeSource).toContain('return false');
    expect(bridgeSource).toContain('insertWithOnConflict(');
    expect(bridgeSource).toContain('!= -1L');
  });
});
