import { test, expect, schemaFixture } from './fixtures.js';
import { mkdir } from 'node:fs/promises';

const sizes = [
  [320, 568], [390, 844], [768, 1024], [1024, 600], [1440, 960], [844, 390],
];

for (const [width, height] of sizes) {
  test(`responsive editing and dialogs at ${width}x${height}`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await mkdir('artifacts/responsive', { recursive: true });
    await page.goto('/');
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
    await expect(page.getByText('Schemas valid', { exact: true })).toBeVisible();
    const fitsPage = async () => {
      const bounds = await page.evaluate(() => ({ width: document.documentElement.scrollWidth, height: document.documentElement.scrollHeight }));
      expect(bounds.width).toBeLessThanOrEqual(width);
      expect(bounds.height).toBeLessThanOrEqual(height);
    };
    await fitsPage();
    const exportButton = page.getByRole('button', { name: 'Export ZIP', exact: true });
    await expect(exportButton).toBeInViewport();

    // A field's edit action must be reachable without horizontal table scrolling.
    const edit = page.getByRole('button', { name: 'Edit id', exact: true });
    if (width <= 1024) {
      expect(await page.locator('.table-scroll').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    }
    await edit.scrollIntoViewIfNeeded();
    const bounds = await edit.boundingBox();
    expect(bounds.x).toBeGreaterThanOrEqual(0);
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
    if (width <= 1024) {
      expect(bounds.width).toBeGreaterThanOrEqual(44);
      expect(bounds.height).toBeGreaterThanOrEqual(44);
      await page.getByRole('button', { name: 'Open file browser' }).click();
      await expect(page.getByRole('dialog', { name: 'Schema files' })).toBeVisible();
      await page.keyboard.press('Escape');
    }
    await edit.click();
    const form = page.getByRole('dialog', { name: 'Edit field' });
    await expect(form.getByLabel('Field name')).toBeFocused();
    await form.getByRole('button', { name: 'Save changes', exact: true }).scrollIntoViewIfNeeded();
    await expect(form.getByRole('button', { name: 'Save changes', exact: true })).toBeInViewport();
    await fitsPage();
    if (width === 320 || height === 390) await page.screenshot({ path: `artifacts/responsive/${width}x${height}-form.png` });
    await page.keyboard.press('Escape');
    await expect(form).not.toBeVisible();

    for (const name of ['Compare', 'Proto source', 'Edit']) {
      await page.getByRole('tab', { name, exact: true }).click();
      await fitsPage();
      if (name === 'Compare') await page.keyboard.press('Escape');
    }
    await exportButton.click();
    const dialog = page.getByRole('dialog', { name: 'Export workspace' });
    await dialog.getByText(`View ${(await schemaFixture()).files.length} files to export`, { exact: true }).click();
    const download = dialog.getByRole('button', { name: 'Download ZIP', exact: true });
    await download.scrollIntoViewIfNeeded();
    await expect(download).toBeInViewport();
    await fitsPage();
    if (width === 320 || height === 390) await page.screenshot({ path: `artifacts/responsive/${width}x${height}-export.png` });
    await page.keyboard.press('Escape');
    await page.locator('.editor').evaluate(element => { element.scrollTop = 0; });
    await page.screenshot({ path: `artifacts/responsive/${width}x${height}.png` });
  });
}
