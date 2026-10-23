import { test, expect, schemaFixture } from './fixtures.js';
import { mkdir, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { unzipSync, strFromU8 } from 'fflate';

const artifactDirectory = fileURLToPath(new URL('../artifacts/', import.meta.url));
const schemaPath = 'game/model/shop.proto';

async function saveScreenshot(page, name) {
  await mkdir(artifactDirectory, { recursive: true });
  await page.screenshot({ path: `${artifactDirectory}/${name}.png`, fullPage: true });
}

async function saveForm(page) {
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(dialog).not.toBeVisible();
}

async function addField(page, name, { type = 'string', rule = 'single', valueType = 'string' } = {}) {
  await page.getByRole('button', { name: 'Add field', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Add field' });
  await dialog.getByLabel('Field name').fill(name);
  await dialog.getByLabel('Data type').selectOption(type);
  if (type === 'map') {
    await dialog.getByLabel('Key type').selectOption('string');
    await dialog.getByLabel('Value type').selectOption(valueType);
  } else {
    await dialog.getByLabel('Field shape').selectOption(rule);
  }
  await saveForm(page);
}

async function addDeclaration(page, kind, name) {
  await page.getByRole('button', { name: kind === 'message' ? 'Message' : 'Enum', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: `Add ${kind}` });
  await dialog.getByLabel(`${kind === 'message' ? 'Message name' : 'Enum name'}`).fill(name);
  await saveForm(page);
}

// The browser assertions below intentionally use actual declarations and complete
// downloaded files so a visually successful mutation cannot hide source loss.

test('real schemas remain intact through editing, recovery, failed import and ZIP export', async ({ page, request }) => {
  await page.setViewportSize({ width: 1440, height: 960 });
  const baseline = await schemaFixture();
  expect(baseline.revision.source).toBe('github');
  expect(baseline.files.length).toBeGreaterThan(5);
  page.on('dialog', dialog => dialog.accept());
  await page.goto('/');
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await expect(page.getByText('Schemas valid', { exact: true })).toBeVisible();
  await expect(page.getByRole('banner').getByText('GitHub', { exact: true })).toBeVisible();

  const search = page.getByRole('textbox', { name: 'Search schemas' });
  await search.fill('missing-schema-no-results');
  await expect(page.getByText('No schemas found', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Clear search' }).first().click();
  await search.fill('game/model/shop.proto');
  const files = page.getByRole('navigation', { name: 'Schema files' });
  await files.getByRole('button', { name: `Open ${schemaPath}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: 'ShopDailySnapshotProto', exact: true })).toBeVisible();

  await addField(page, 'review_note');
  const added = page.getByRole('row').filter({ has: page.getByText('review_note', { exact: true }) });
  await expect(added).toContainText('5'); // Existing 1-3 plus reserved 4 must allocate 5.
  await page.getByRole('button', { name: 'Edit review_note', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Field name').fill('review_title');
  await saveForm(page);
  await expect(page.getByRole('button', { name: 'Edit review_title', exact: true })).toBeVisible();
  await addField(page, 'review_tags', { rule: 'repeated' });
  await expect(page.getByRole('row').filter({ has: page.getByText('review_tags', { exact: true }) })).toContainText('List');
  await addField(page, 'review_counts', { type: 'map', valueType: 'int32' });
  await expect(page.getByRole('row').filter({ has: page.getByText('review_counts', { exact: true }) })).toContainText('map<string, int32>');

  await addDeclaration(page, 'message', 'ReviewMetadata');
  await expect(page.getByRole('heading', { name: 'ReviewMetadata', exact: true })).toBeVisible();
  await addField(page, 'author');
  await addDeclaration(page, 'enum', 'ReviewState');
  await page.getByRole('button', { name: 'Add value', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Value name').fill('REVIEW_READY');
  await page.getByRole('dialog').getByRole('spinbutton').fill('1');
  await saveForm(page);
  await expect(page.getByText('REVIEW_READY', { exact: true })).toBeVisible();

  await page.getByRole('tab', { name: 'Compare' }).click();
  const diff = page.getByRole('region', { name: 'Compare original and draft' });
  await expect(diff).toBeVisible();
  await expect(diff.getByText('ORIGINAL', { exact: true })).toBeVisible();
  await expect(diff.getByText('DRAFT', { exact: true })).toBeVisible();
  await expect(diff).toContainText('review_title');
  await page.keyboard.press('Escape');
  await page.getByRole('tab', { name: 'Proto source', exact: true }).click();
  await expect(page.getByRole('tabpanel')).toContainText('reserved "schema_version"');
  await expect(page.getByRole('tabpanel')).toContainText('option csharp_namespace = "Game.Server.Proto"');
  await page.reload();
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'ReviewState', exact: true })).toBeVisible();
  await expect(page.getByText('REVIEW_READY', { exact: true })).toBeVisible();

  const persisted = await page.evaluate(() => JSON.parse(localStorage.getItem('proto-studio-workspace-v1')));
  expect(persisted.files.find(file => file.path === schemaPath).content).toContain('review_title');
  await page.route('**/api/github', route => route.fulfill({ status: 422, contentType: 'application/json', body: JSON.stringify({ error: 'No schemas were found at this commit.' }) }));
  await page.getByRole('button', { name: 'Source settings' }).click();
  const importDialog = page.getByRole('dialog', { name: 'Source settings' });
  await importDialog.getByLabel('Repository', { exact: true }).fill('owner/repo');
  await importDialog.getByLabel('Full commit SHA').fill('a'.repeat(40));
  await importDialog.getByRole('button', { name: 'Save & load' }).click();
  await expect(importDialog.getByRole('alert')).toContainText('No schemas were found');
  await page.keyboard.press('Escape');
  await expect(importDialog).not.toBeVisible();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('proto-studio-workspace-v1')).files)).toEqual(persisted.files);

  const analyzeResponse = page.waitForResponse(response => response.url().endsWith('/api/analyze') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Validate', exact: true }).click();
  expect((await (await analyzeResponse).json()).valid).toBe(true);
  await expect(page.getByText('Schemas valid', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Export ZIP', exact: true }).click();
  const exportDialog = page.getByRole('dialog', { name: 'Export workspace' });
  await expect(exportDialog).toContainText(`${baseline.files.length} .proto files`);
  await expect(exportDialog).toContainText('1 changed file');
  await expect(exportDialog).toContainText('Folder structure');
  await saveScreenshot(page, 'export-dialog');
  const downloadEvent = page.waitForEvent('download');
  await exportDialog.getByRole('button', { name: 'Download ZIP', exact: true }).click();
  const download = await downloadEvent;
  expect(download.suggestedFilename()).toBe('proto-studio-schemas.zip');
  const zip = unzipSync(await readFile(await download.path()));
  expect(Object.keys(zip).sort()).toEqual(baseline.files.map(file => file.path).sort());
  for (const original of baseline.files) {
    const actual = strFromU8(zip[original.path]);
    if (original.path !== schemaPath) expect(actual).toBe(original.content);
    else {
      expect(actual).toBe(persisted.files.find(file => file.path === schemaPath).content);
      for (const preserved of ['import "game/blueprint/shop.proto";', 'option csharp_namespace = "Game.Server.Proto";', 'reserved 4;', 'reserved "schema_version";', '// Prices and rewards are frozen until expiry; purchase counts can change.', 'reserved 3, 8;']) expect(actual).toContain(preserved);
      expect(actual).toMatch(/string review_title = 5;/);
      expect(actual).toContain('repeated string review_tags');
      expect(actual).toContain('map<string, int32> review_counts');
      expect(actual).toContain('message ReviewMetadata');
      expect(actual).toContain('REVIEW_READY = 1;');
    }
  }
  await page.getByRole('tab', { name: 'Edit', exact: true }).click();
  await saveScreenshot(page, 'desktop');
});

test('mobile workspace keeps actions visible and supports keyboard dismissal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await expect(page.getByText('Schemas valid', { exact: true })).toBeVisible();
  const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  expect(documentWidth).toBeLessThanOrEqual(390);
  await expect(page.getByRole('button', { name: 'Export ZIP', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Validate', exact: true })).toBeVisible();
  const openFiles = page.getByRole('button', { name: 'Open file browser' });
  await openFiles.click();
  const closeFiles = page.getByRole('button', { name: 'Close file browser' });
  await expect(closeFiles).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(closeFiles).not.toBeVisible();
  await expect(openFiles).toBeFocused();
  await openFiles.click();
  const search = page.getByRole('textbox', { name: 'Search schemas' });
  await search.fill('common_init');
  await page.getByRole('button', { name: 'Open game/app/common/common_init.proto', exact: true }).click();
  await expect(closeFiles).not.toBeVisible();
  await page.getByRole('button', { name: 'Add field', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Add field' });
  await expect(dialog.getByLabel('Field name')).toBeFocused();
  await saveScreenshot(page, 'form');
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  const declarationSelector = page.getByRole('combobox', { name: 'Selected definition' });
  await declarationSelector.selectOption('game.app.CommonInitResponseProto');
  await expect(page.getByRole('heading', { name: 'CommonInitResponseProto', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await saveScreenshot(page, 'mobile');
});


test('laptop workspace keeps navigation and export usable', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await expect(page.getByText('Schemas valid', { exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Schema files' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Export ZIP', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(1280);
  await saveScreenshot(page, 'laptop');
});
