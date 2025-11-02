import { test, expect } from '@playwright/test';

test.describe('Jade Tipi Frontend Tests', () => {
  test('homepage loads successfully', async ({ page }) => {
    await page.goto('/');

    // Check page title
    await expect(page).toHaveTitle('Jade Tipi');

    // Check main heading
    await expect(page.getByRole('heading', { name: 'Jade Tipi - MongoDB Document Manager' })).toBeVisible();

    // Check sign-in button is present
    await expect(page.getByRole('button', { name: 'Sign In with Keycloak' })).toBeVisible();
  });

  test('navigation links are present', async ({ page }) => {
    await page.goto('/');

    // Check navigation links in header
    await expect(page.locator('header nav a', { hasText: 'Home' })).toBeVisible();
    await expect(page.locator('header nav a', { hasText: 'Documents' })).toBeVisible();
    await expect(page.locator('header nav a', { hasText: 'Create' })).toBeVisible();
  });

  test('create document page requires authentication', async ({ page }) => {
    await page.goto('/document/create');

    // Should show authentication required message
    await expect(page.getByRole('heading', { name: 'Sign in to create documents' })).toBeVisible();
    await expect(page.getByText('You need to authenticate with Keycloak')).toBeVisible();
  });

  test('documents list page requires authentication', async ({ page }) => {
    await page.goto('/list');

    // Should show authentication required message
    await expect(page.getByRole('heading', { name: 'Sign in to view documents' })).toBeVisible();
    await expect(page.getByText('Authenticate with Keycloak to list and browse')).toBeVisible();
  });

  test('NextAuth API endpoint is working', async ({ request }) => {
    const response = await request.get('/api/auth/providers');
    expect(response.ok()).toBeTruthy();

    const providers = await response.json();
    expect(providers).toHaveProperty('keycloak');
    expect(providers.keycloak).toMatchObject({
      id: 'keycloak',
      name: 'Keycloak',
      type: 'oidc',
    });
  });

  test('environment variables are properly configured', async ({ page }) => {
    await page.goto('/');

    // The fact that the auth provider endpoint works means KEYCLOAK_ISSUER is configured
    const response = await page.request.get('/api/auth/providers');
    expect(response.ok()).toBeTruthy();

    const providers = await response.json();
    expect(providers.keycloak.signinUrl).toContain('localhost:3000');
  });
});
