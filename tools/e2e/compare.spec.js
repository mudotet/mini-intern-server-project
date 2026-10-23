import { test, expect, schemaFixture } from './fixtures.js';

for (const theme of ['light', 'dark']) {
  test(`English editor shows old and new values in split and unified compare (${theme})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: theme });
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
    await page.getByRole('button', { name: 'Edit id', exact: true }).click();
    const form = page.getByRole('dialog', { name: 'Edit field', exact: true });
    await form.getByLabel('Field name', { exact: false }).fill('package_id');
    await form.getByRole('button', { name: 'Save changes', exact: true }).click();
    await expect(form).not.toBeVisible();
    if (theme === 'light') await expect(page.getByRole('status')).not.toBeVisible({ timeout: 7000 });
    await page.getByRole('tab', { name: /^Compare/ }).click();
    const diff = page.getByRole('region', { name: 'Compare original and draft' });
    await expect(diff.getByText('ORIGINAL', { exact: true })).toBeVisible();
    await expect(diff.getByText('DRAFT', { exact: true })).toBeVisible();
    await expect(diff.locator('.code-line.removed')).toContainText('id');
    await expect(diff.locator('.code-line.added')).toContainText('package_id');
    await expect(diff.locator('.code-line.added .diff-marker')).toHaveText('+');
    await expect(diff.locator('.code-line.removed .diff-marker')).toHaveText('−');
    await expect(diff.locator('.inline-added')).toContainText('package_id');
    await diff.getByLabel('Changes only').check();
    await expect(diff.locator('.code-line.unchanged')).toHaveCount(0);
    await diff.getByRole('button', { name: 'Unified', exact: true }).click();
    await expect(diff.locator('.unified-diff .code-line.removed')).toContainText('id');
    await expect(diff.locator('.unified-diff .code-line.added')).toContainText('package_id');
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(diff.getByRole('button', { name: 'Split', exact: true })).toBeInViewport();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });
}

for (const [width, height] of [[1440, 900], [390, 844], [844, 390]]) {
  test(`compare opens above the scrolled editor and returns focus at ${width}x${height}`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await page.goto('/');
    await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
    const editor = page.locator('.editor');
    await editor.evaluate(element => { element.scrollTop = 180; });
    const compare = page.getByRole('tab', { name: /^Compare/ });
    await compare.click();
    const dialog = page.getByRole('dialog', { name: 'Compare schemas', exact: true });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Back to editor', exact: true })).toBeFocused();
    await expect(dialog.getByRole('button', { name: 'Back to editor', exact: true })).toBeInViewport();
    await expect(dialog.getByRole('button', { name: 'Unified', exact: true })).toBeInViewport();
    await expect(dialog.getByLabel('Changes only')).toBeInViewport();
    await expect(dialog).toContainText('game/blueprint/shop.proto');
    const bounds = await dialog.boundingBox();
    expect(bounds.width).toBeGreaterThan(width * .9);
    expect(bounds.height).toBeLessThanOrEqual(height);
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(compare).toBeFocused();
    await expect(page.getByRole('tab', { name: 'Edit', exact: true })).toHaveAttribute('aria-selected', 'true');
    await compare.click();
    await dialog.getByRole('button', { name: 'Back to editor', exact: true }).click();
    await expect(dialog).not.toBeVisible();
  });
}
