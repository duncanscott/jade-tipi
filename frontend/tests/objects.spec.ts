import { test, expect } from '@playwright/test';

// TASK-057 entity object view. Like the other suites, these run
// unauthenticated and assert the sign-in gate; authenticated rendering is
// exercised against a live backend manually.
test.describe('Object view', () => {
  test('object detail page requires authentication', async ({ page }) => {
    await page.goto('/objects/018fd849-2a45-7555-8e05-eeeeeeeeeeee~jade-tipi-org~dev~ent~sample_x1');

    await expect(page.getByRole('heading', { name: 'Sign in to view objects' })).toBeVisible();
    await expect(page.getByText('Authenticate with Keycloak to inspect materialized objects')).toBeVisible();
  });
});
