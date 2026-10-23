import { test, expect, schemaFixture } from './fixtures.js';

test('overview charts use real schema totals and drill into the editor', async ({ page, request }) => {
  const baseline = await schemaFixture();
  const messages = baseline.analysis.files.flatMap(file => file.messages);
  const fields = messages.reduce((sum, message) => sum + message.fields.length, 0);
  await page.goto('/');
  const overview = page.getByRole('region', { name: 'Schema overview' });
  await expect(overview).toBeVisible();
  await expect(overview.getByTestId('stat-files')).toHaveText(String(baseline.files.length));
  await expect(overview.getByTestId('stat-messages')).toHaveText(String(messages.length));
  await expect(overview.getByTestId('stat-fields')).toHaveText(String(fields));
  await expect(overview.getByTestId('stat-changed')).toHaveText('0');
  const chart = overview.getByRole('region', { name: 'Fields by file' });
  const largest = baseline.analysis.files.map(file => ({ path: file.path, count: file.messages.reduce((sum, m) => sum + m.fields.length, 0) })).sort((a,b) => b.count-a.count || a.path.localeCompare(b.path))[0];
  await chart.getByRole('button', { name: `Open ${largest.path}, ${largest.count} fields`, exact: true }).click();
  await expect(page.getByRole('button', { name: 'Schema editor', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.breadcrumb')).toContainText(largest.path);
  await page.getByRole('button', { name: 'Overview', exact: true }).click();
  await expect(overview).toBeVisible();
});

test('theme follows system initially and persists an explicit preference across reloads', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.getByRole('button', { name: 'Switch to dark mode', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: 'Switch to light mode', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
});

for (const width of [320, 390, 768, 1440]) {
  test(`overview remains usable at ${width}px in both themes`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
    for (const theme of ['light', 'dark']) {
      await page.emulateMedia({ colorScheme: theme });
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
      await page.getByRole('region', { name: 'Import relationships' }).scrollIntoViewIfNeeded();
      await expect(page.getByLabel('File in diagram')).toBeVisible();
    }
  });
}

test('import explorer follows compiled dependencies and statistics refresh after editing', async ({ page, request }) => {
  const baseline = await schemaFixture();
  const source = baseline.analysis.files.find(file => file.imports?.some(path => baseline.files.some(item => item.path === path)));
  expect(source).toBeTruthy();
  const target = source.imports.find(path => baseline.files.some(file => file.path === path));
  await page.goto('/');
  const map = page.getByRole('region', { name: 'Import relationships' });
  await page.getByLabel('File in diagram').selectOption(source.path);
  await map.locator('.map-column').nth(2).getByRole('button', { name: target, exact: true }).click();
  await expect(page.getByLabel('File in diagram')).toHaveValue(target);
  await expect(map.locator('.map-column').nth(0)).toContainText(source.path);
  const fields = baseline.analysis.files.flatMap(file => file.messages).reduce((n, message) => n + message.fields.length, 0);
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await page.getByRole('button', { name: 'Add field', exact: true }).click();
  const form = page.getByRole('dialog', { name: 'Add field', exact: true });
  await form.getByLabel('Field name').fill('dashboard_note');
  await form.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(form).not.toBeVisible();
  await page.getByRole('button', { name: 'Overview', exact: true }).click();
  await expect(page.getByTestId('stat-fields')).toHaveText(String(fields + 1));
  await expect(page.getByTestId('stat-changed')).toHaveText('1');
});
