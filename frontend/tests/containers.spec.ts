import { test, expect } from '@playwright/test';

// TASK-053 container view. Like the other suites, these run unauthenticated
// and assert the sign-in gate and navigation surface; authenticated rendering
// is exercised against a live backend manually (see the task runbook notes).
test.describe('Containers view', () => {
  test('containers nav link is present', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('header nav a', { hasText: 'Containers' })).toBeVisible();
  });

  test('containers entry page requires authentication', async ({ page }) => {
    await page.goto('/containers');

    await expect(page.getByRole('heading', { name: 'Sign in to view containers' })).toBeVisible();
    await expect(page.getByText('Authenticate with Keycloak to inspect materialized containers')).toBeVisible();
  });

  test('container detail page requires authentication', async ({ page }) => {
    await page.goto('/containers/018fd84a-51a7-7e96-8de1-000000000001~jade-tipi-org~dev~loc~plate_0001');

    await expect(page.getByRole('heading', { name: 'Sign in to view containers' })).toBeVisible();
  });
});
