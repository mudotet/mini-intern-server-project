import { test, expect, schemaFixture } from './fixtures.js';

test('nested navigation, declaration outline and export cancellation preserve the workspace', async ({ page, request }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await expect(page.getByText('Schemas valid', { exact: true })).toBeVisible();
  const navigation = page.getByRole('navigation', { name: 'Schema files' });
  const gameFolder = navigation.getByRole('button', { name: 'Folder game', exact: true });
  const blueprintFolder = navigation.getByRole('button', { name: 'Folder game/blueprint', exact: true });
  const blueprintFile = navigation.getByRole('button', { name: 'Open game/blueprint/shop.proto', exact: true });
  await expect(gameFolder).toHaveAttribute('aria-expanded', 'true');
  await expect(blueprintFolder).toHaveAttribute('aria-expanded', 'true');
  await expect(blueprintFile).toHaveAttribute('aria-current', 'page');
  await blueprintFolder.click();
  await expect(blueprintFolder).toHaveAttribute('aria-expanded', 'false');
  await expect(blueprintFile).not.toBeVisible();
  await blueprintFolder.click();
  await expect(blueprintFile).toBeVisible();
  await gameFolder.click();
  await expect(gameFolder).toHaveAttribute('aria-expanded', 'false');
  await expect(blueprintFolder).not.toBeVisible();
  await gameFolder.click();

  await blueprintFolder.click();
  await expect(blueprintFolder).toHaveAttribute('aria-expanded', 'false');
  const search = page.getByRole('textbox', { name: 'Search schemas' });
  await search.fill('shop.proto');
  const modelFolder = navigation.getByRole('button', { name: 'Folder game/model', exact: true });
  const modelFile = navigation.getByRole('button', { name: 'Open game/model/shop.proto', exact: true });
  await expect(blueprintFile).toBeVisible();
  await expect(modelFile).toBeVisible();
  await expect(modelFolder).toHaveAttribute('aria-expanded', 'true');
  await expect(navigation.getByRole('button', { name: 'Open game/error.proto', exact: true })).not.toBeVisible();
  await modelFile.click();
  await search.fill('');
  await expect(modelFolder).toHaveAttribute('aria-expanded', 'true');
  await expect(modelFile).toHaveAttribute('aria-current', 'page');
  await expect(blueprintFolder).toHaveAttribute('aria-expanded', 'false');
  await expect(blueprintFile).not.toBeVisible();
  await blueprintFolder.click();

  const outline = page.getByRole('navigation', { name: 'File outline' });
  const target = outline.getByRole('button', { name: 'Select game.model.ShopDailyOfferSnapshotProto', exact: true });
  await target.click();
  await expect(target).toHaveAttribute('aria-current', 'true');
  await expect(page.getByRole('heading', { name: 'ShopDailyOfferSnapshotProto', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit items', exact: true })).toBeVisible();
  await blueprintFile.click();
  await outline.getByRole('button', { name: 'Select game.blueprint.ShopPackage.Price', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Price', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit currency', exact: true })).toBeVisible();

  const before = await page.evaluate(() => JSON.parse(localStorage.getItem('proto-studio-workspace-v1')).files);
  const baseline = await schemaFixture();
  let downloads = 0;
  page.on('download', () => downloads++);
  await page.getByRole('button', { name: 'Export ZIP', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Export workspace' });
  await expect(dialog).toContainText(`${baseline.files.length} .proto files`);
  await expect(dialog).toContainText('0 changed files');
  await expect(dialog).toContainText('Folder structure');
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  expect(downloads).toBe(0);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('proto-studio-workspace-v1')).files)).toEqual(before);
});

test('workspace tracking opens an edited file in diff', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await expect(page.getByText('Schemas valid', { exact: true })).toBeVisible();
  const tracker = page.getByRole('region', { name: 'Workspace changes' });

  await tracker.locator('summary').click();
  await expect(tracker).toContainText('Your draft matches the original');
  await tracker.locator('summary').click();
  await page.getByRole('button', { name: 'Edit id', exact: true }).click();
  const form = page.getByRole('dialog', { name: 'Edit field' });
  await form.getByLabel('Field name').fill('tracked_package_id');
  await form.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(form).not.toBeVisible();
  await expect(tracker.locator('summary')).toContainText('1 changed file');
  await page.getByRole('navigation', { name: 'Schema files' }).getByRole('button', { name: 'Open game/error.proto', exact: true }).click();
  await tracker.locator('summary').click();
  await tracker.getByRole('button', { name: 'game/blueprint/shop.proto', exact: true }).click();
  await expect(page.locator('#tab-diff')).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('dialog', { name: 'Compare schemas', exact: true })).toContainText('tracked_package_id');
  await expect(tracker.locator('details')).not.toHaveAttribute('open');
});
