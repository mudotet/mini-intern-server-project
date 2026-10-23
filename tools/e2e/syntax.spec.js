import { test, expect, schemaFixture } from './fixtures.js';

for (const theme of ['light', 'dark']) {
  test(`syntax colors preserve source and coexist with diff highlights (${theme})`, async ({ page, request }) => {
    const baseline = await schemaFixture();
    const original = baseline.files.find(file => file.path === 'game/blueprint/shop.proto').content;
    await page.emulateMedia({ colorScheme: theme });
    await page.goto('/');
    await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
    await page.getByRole('tab', { name: 'Proto source', exact: true }).click();
    const source = page.locator('.source-panel');
    expect(await source.locator('.code-line code').allTextContents()).toEqual(original.split('\n'));
    const colors = await source.evaluate(element => ['keyword', 'type', 'string', 'number', 'comment'].map(kind => getComputedStyle(element.querySelector(`.proto-${kind}`)).color));
    expect(new Set(colors).size).toBe(5);
    await page.getByRole('tab', { name: 'Edit', exact: true }).click();
    await page.getByRole('button', { name: 'Edit id', exact: true }).click();
    await page.getByRole('dialog').getByLabel('Field name').fill('package_id');
    await page.getByRole('dialog').getByRole('button', { name: 'Save changes', exact: true }).click();
    await page.getByRole('tab', { name: /^Compare/ }).click();
    const diff = page.getByRole('dialog', { name: 'Compare schemas', exact: true });
    await expect(diff.locator('.proto-keyword').first()).toBeVisible();
    await expect(diff.locator('.inline-added')).toContainText('package_id');
    await expect(diff.locator('.inline-removed')).toContainText('id');
    await diff.getByLabel('Changes only').check();
    await diff.getByRole('button', { name: 'Unified', exact: true }).click();
    await expect(diff.locator('.added .proto-type')).toHaveText('string');
    await expect(diff.locator('.removed .proto-type')).toHaveText('string');
    await expect(diff.locator('.added .inline-added')).toHaveText('package_id');
  });
}

test('light-mode code stays readable on source, added, removed and inline-highlight backgrounds', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  await page.getByRole('button', { name: 'Schema editor', exact: true }).click();
  await page.getByRole('tab', { name: 'Proto source', exact: true }).click();
  await expect(page.locator('.source-scroll pre')).toHaveCSS('font-size', '13px');
  await expect(page.locator('.proto-comment').first()).toHaveCSS('font-style', 'normal');
  const checkContrast = async root => {
    const failures = await root.evaluate(element => {
      const rgba = value => (value.match(/[\d.]+/g) || []).map(Number);
      const luminance = rgb => rgb.slice(0, 3).map(value => value / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4).reduce((sum, value, index) => sum + value * [.2126, .7152, .0722][index], 0);
      return [...element.querySelectorAll('.code-line [class^="proto-"]')].filter(node => node.textContent.trim()).flatMap(node => {
        const ancestors = []; for (let current = node; current; current = current.parentElement) ancestors.push(current);
        let background = [255, 255, 255];
        for (const ancestor of ancestors.reverse()) {
          const color = rgba(getComputedStyle(ancestor).backgroundColor), alpha = color[3] ?? 1;
          if (color.length >= 3) background = background.map((value, index) => color[index] * alpha + value * (1 - alpha));
        }
        const foreground = rgba(getComputedStyle(node).color);
        const a = luminance(foreground), b = luminance(background);
        const contrast = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
        return contrast < 7 ? [{ token: node.textContent, kind: node.className, contrast: +contrast.toFixed(2) }] : [];
      });
    });
    expect(failures).toEqual([]);
  };
  await checkContrast(page.locator('.source-panel'));
  await page.getByRole('tab', { name: 'Edit', exact: true }).click();
  await page.getByRole('button', { name: 'Edit id', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Field name').fill('package_id');
  await page.getByRole('dialog').getByRole('button', { name: 'Save changes', exact: true }).click();
  await page.getByRole('tab', { name: /^Compare/ }).click();
  await checkContrast(page.getByRole('dialog', { name: 'Compare schemas', exact: true }));
});
