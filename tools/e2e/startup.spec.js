import { test, expect } from '@playwright/test';
import { localWorkspace } from '../test/fixtures/local-workspace.mjs';

const repository = 'mudotet/proto_mini-intern-server-project';
const sha = '06a40d0da3a6c14d79acaf0fc40be086a0ad93db';

test('fresh start has no schemas and loads only after a full commit is submitted', async ({ page }) => {
  const baseline = await localWorkspace();
  const imported = { ...baseline, revision: { source: 'github', repository, sha, label: sha } };
  const calls = [];
  page.on('request', request => { if (request.url().includes('/api/')) calls.push(request.url()); });
  await page.route('**/api/github', route => route.fulfill({ json: imported }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
  await expect(page.getByTestId('stat-files')).toHaveText('0');
  await expect(page.locator('main form')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Schema editor', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Export ZIP', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Source settings', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Source settings', exact: true })).toBeVisible();
  await expect(page.getByLabel('Repository', { exact: true })).toBeEmpty();
  await expect(page.getByLabel('Full commit SHA')).toBeEmpty();
  await expect(page.getByText('Local demo', { exact: true })).toHaveCount(0);

  expect(calls).toEqual([]);
  await page.getByLabel('Repository', { exact: true }).fill(repository);
  await page.getByLabel('Full commit SHA').fill('main');
  await page.getByRole('button', { name: 'Save & load', exact: true }).click();
  expect(calls).toEqual([]);
  await page.getByLabel('Full commit SHA').fill(sha);
  await page.getByRole('button', { name: 'Save & load', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
  await expect(page.getByTestId('stat-files')).toHaveText(String(baseline.files.length));
  expect(calls.filter(url => url.endsWith('/api/github'))).toHaveLength(1);
  await page.getByRole('button', { name: 'Source settings', exact: true }).click();
  await expect(page.getByLabel('Repository', { exact: true })).toHaveValue(repository);
  await expect(page.getByLabel('Full commit SHA')).toHaveValue(sha);
  await page.getByLabel('Full commit SHA').fill('a'.repeat(40));
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.locator('.revision code')).toHaveText(sha.slice(0, 8));
  expect(calls.filter(url => url.endsWith('/api/github'))).toHaveLength(1);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
  expect(calls.filter(url => url.endsWith('/api/github'))).toHaveLength(1);
});

test('old local-demo drafts do not populate the initial workspace', async ({ page }) => {
  const baseline = await localWorkspace();
  await page.addInitScript(data => localStorage.setItem('proto-studio-workspace-v1', JSON.stringify({ files: data.files, original: data.files, revision: data.revision, originalAnalysis: data.analysis })), baseline);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
  await expect(page.getByTestId('stat-files')).toHaveText('0');
  await expect(page.getByRole('navigation', { name: 'Schema files' }).getByRole('button')).toHaveCount(0);
});

test('failed import leaves the workspace empty and retryable', async ({ page }) => {
  await page.route('**/api/github', route => route.fulfill({ status: 422, json: { error: 'No .proto files were found at this commit.' } }));
  await page.goto('/');
  await page.getByRole('button', { name: 'Source settings', exact: true }).click();
  await page.getByLabel('Repository', { exact: true }).fill(repository);
  await page.getByLabel('Full commit SHA').fill(sha);
  await page.getByRole('button', { name: 'Save & load', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('No .proto files');
  await expect(page.getByRole('button', { name: 'Save & load', exact: true })).toBeEnabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
});


test('empty start supports both themes and blocked storage on a small screen', async ({ page }) => {
  await page.addInitScript(() => { Object.defineProperty(window, 'localStorage', { get() { throw new Error('Storage unavailable'); } }); });
  await page.setViewportSize({ width: 320, height: 568 });
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Workspace overview', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Switch to dark mode', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);
  await page.getByRole('button', { name: 'Source settings', exact: true }).click();
  await page.getByRole('button', { name: 'Save & load', exact: true }).scrollIntoViewIfNeeded();
  await expect(page.getByRole('button', { name: 'Save & load', exact: true })).toBeInViewport();
});
