import js from '@eslint/js';
import globals from 'globals';

export default [
  { ignores: ['node_modules/', 'target/', 'test-results/', 'playwright-report/'] },
  js.configs.recommended,
  {
    // Admin UI: plain browser scripts (IIFEs), no bundler.
    files: ['src/main/resources/static/js/**/*.js'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'script',
      globals: { ...globals.browser, I18N: 'readonly' },
    },
    rules: {
      // Empty catch blocks guard localStorage / Monaco calls on purpose.
      'no-empty': ['error', { allowEmptyCatch: true }],
      'no-unused-vars': ['error', { caughtErrors: 'none' }],
    },
  },
  {
    files: ['tests/**/*.{js,mjs}', 'playwright.config.mjs', 'eslint.config.mjs'],
    languageOptions: { ecmaVersion: 2022, sourceType: 'module', globals: globals.node },
  },
];
