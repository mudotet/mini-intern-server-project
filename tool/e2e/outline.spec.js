import { test, expect, schemaFixture } from './fixtures.js';

for (const theme of ['light', 'dark']) {
  test(`outline explains selection and nested definitions (${theme})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: theme });
    await page.goto('/');
    await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
    const outline = page.getByRole('navigation', { name: 'File outline' });
    const selected = outline.getByRole('button', { name: 'Select game.blueprint.ShopPackage', exact: true });
    await expect(selected).toHaveAttribute('aria-current', 'true');
    await expect(selected.getByText('Editing', { exact: true })).toBeVisible();
    await expect(selected).toContainText('4 fields');
    const nested = outline.getByRole('button', { name: 'Select game.blueprint.ShopPackage.Price', exact: true });
    await expect(nested).toContainText('1 field');
    await expect(nested.locator('..').locator('..')).toHaveClass('outline-children');
    await nested.click();
    await expect(nested.getByText('Editing', { exact: true })).toBeVisible();
    await expect(selected.getByText('Editing', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Price', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Edit currency', exact: true })).toBeVisible();
    await expect(outline.getByText('Editing', { exact: true })).toHaveCount(1);
    await expect(page.locator('.outline-file-status')).toContainText('Matches original');
    await page.getByRole('button', { name: 'Edit currency', exact: true }).click();
    await page.getByRole('dialog').getByLabel('Field name').fill('currency_code');
    await page.getByRole('dialog').getByRole('button', { name: 'Save changes', exact: true }).click();
    await expect(page.locator('.outline-file-status')).toContainText('Draft has changes');
  });
}
