import { test as base, expect } from '@playwright/test';
import { localWorkspace } from '../test/fixtures/local-workspace.mjs';

// Existing editor tests begin with a previously imported GitHub draft.
// Fresh-start and import-form behavior are covered separately in startup.spec.js.
let fixture;
export function schemaFixture() {
  fixture ||= localWorkspace().then(data => ({ ...data, revision: { source: 'github', repository: 'mudotet/proto_mini-intern-server-project', sha: 'e1428e6434a955bbdfba7dac29c2023ff3560303', label: 'e1428e6434a955bbdfba7dac29c2023ff3560303' } }));
  return fixture;
}
export const test = base.extend({
  savedGithubWorkspace: [async ({ page }, use) => {
    const data = await schemaFixture();
    await page.addInitScript(data => {
      try {
        if (!localStorage.getItem('proto-studio-workspace-v1')) localStorage.setItem('proto-studio-workspace-v1', JSON.stringify({ files: data.files, original: data.files, originalAnalysis: data.analysis, revision: data.revision, selected: 'game/blueprint/shop.proto', declarationName: 'game.blueprint.ShopPackage' }));
      } catch { /* Blocked storage is covered by the fresh-start tests. */ }
    }, data);
    await use();
  }, { auto: true }],
});
export { expect };
