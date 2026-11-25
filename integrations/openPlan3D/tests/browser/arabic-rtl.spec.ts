import { expect, test } from '@playwright/test';

test('first visit starts in Arabic RTL, preserves the editor, and switches back to LTR', async ({ page }) => {
  await page.addInitScript(() => localStorage.removeItem('o3d_locale'));
  await page.goto('/editor');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByRole('button', { name: 'الإعدادات', exact: true })).toBeVisible();
  await expect(page.getByText('الطابق الأرضي', { exact: true })).toBeVisible();
  const canvas = page.locator('canvas').first();
  if (await canvas.count()) expect(await canvas.evaluate(element => getComputedStyle(element).direction)).toBe('ltr');

  const bodyText = await page.locator('body').innerText();
  expect(bodyText).not.toMatch(/\b(Settings|Projects|New project|Undo|Redo|Wall|Door|Window|Furniture|Floor|Export|Import|Save|Loading|Welcome|Start|Select)\b/);

  await page.getByRole('button', { name: 'الإعدادات', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'الإعدادات', exact: true });
  await dialog.getByRole('button', { name: 'المظهر', exact: true }).click();
  await expect(dialog.getByRole('combobox', { name: 'اللغة', exact: true })).toHaveValue('ar');
  await dialog.getByRole('combobox', { name: 'اللغة', exact: true }).selectOption('en');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await expect(page.getByRole('dialog', { name: 'Settings', exact: true })).toBeVisible();
});

test('Arabic editor and render lab remain within a narrow mobile viewport', async ({ page }) => {
  await page.addInitScript(() => localStorage.removeItem('o3d_locale'));
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('/editor');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  await page.goto('/render-lab');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByRole('heading', { name: 'منظور مختلف.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'تفاعلي', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'تتبع المسار', exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
