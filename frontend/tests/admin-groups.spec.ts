import { test, expect, Page } from '@playwright/test';

/**
 * Playwright coverage for the admin group workflow. Spans three role flows:
 *
 * 1. Unauthenticated — Groups link is not in the header; navigating directly
 *    to /admin/groups shows the sign-in prompt.
 * 2. Authenticated non-admin — session.isAdmin is false; Groups link is not
 *    rendered; navigating to /admin/groups shows the forbidden message.
 * 3. Authenticated admin — session.isAdmin is true; Groups link renders;
 *    navigating to /admin/groups loads the admin page.
 *
 * The authenticated paths are exercised by mocking the NextAuth client
 * session endpoint (/api/auth/session) so we do not need a live Keycloak
 * sign-in flow inside the browser test. The backend fetch is mocked at the
 * API base URL the page uses to keep these tests hermetic to the frontend.
 */

const SESSION_BASE = {
  user: { name: 'Test User', email: 'test@example.com' },
  expires: '2099-01-01T00:00:00.000Z',
};

async function mockSession(page: Page, body: object | null) {
  await page.route('**/api/auth/session', async (route) => {
    if (!body) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: 'null' });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    }
  });
}

async function mockBackendList(page: Page, items: unknown[]) {
  await page.route('**/api/admin/groups', async (route, request) => {
    if (request.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items }),
      });
      return;
    }
    await route.continue();
  });
}

test.describe('Admin groups - unauthenticated', () => {
  test('Groups link is not rendered in header for signed-out users', async ({ page }) => {
    await mockSession(page, null);
    await page.goto('/');

    await expect(page.locator('header nav a', { hasText: 'Home' })).toBeVisible();
    await expect(page.locator('header nav a', { hasText: 'Groups' })).toHaveCount(0);
  });

  test('/admin/groups shows sign-in prompt for signed-out users', async ({ page }) => {
    await mockSession(page, null);
    await page.goto('/admin/groups');

    await expect(page.getByTestId('admin-groups-signin')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Sign in required' })).toBeVisible();
  });
});

test.describe('Admin groups - authenticated non-admin', () => {
  test('Groups link is not rendered for non-admin users', async ({ page }) => {
    await mockSession(page, {
      ...SESSION_BASE,
      accessToken: 'fake-non-admin-token',
      isAdmin: false,
    });
    await page.goto('/');

    await expect(page.locator('header nav a', { hasText: 'Documents' })).toBeVisible();
    await expect(page.locator('header nav a', { hasText: 'Groups' })).toHaveCount(0);
  });

  test('/admin/groups shows forbidden message for non-admin users', async ({ page }) => {
    await mockSession(page, {
      ...SESSION_BASE,
      accessToken: 'fake-non-admin-token',
      isAdmin: false,
    });
    await page.goto('/admin/groups');

    await expect(page.getByTestId('admin-groups-forbidden')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Forbidden' })).toBeVisible();
  });
});

test.describe('Admin groups - authenticated admin', () => {
  test('Groups link is rendered and /admin/groups loads admin page', async ({ page }) => {
    await mockSession(page, {
      ...SESSION_BASE,
      accessToken: 'fake-admin-token',
      isAdmin: true,
    });
    await mockBackendList(page, []);
    await page.goto('/');

    const groupsLink = page.locator('header nav a', { hasText: 'Groups' });
    await expect(groupsLink).toBeVisible();

    await groupsLink.click();
    await expect(page).toHaveURL(/\/admin\/groups/);
    await expect(page.getByRole('heading', { name: 'Groups' })).toBeVisible();
    await expect(page.getByTestId('admin-groups-empty')).toBeVisible();
  });

  test('admin can see existing groups returned by the API', async ({ page }) => {
    await mockSession(page, {
      ...SESSION_BASE,
      accessToken: 'fake-admin-token',
      isAdmin: true,
    });
    await mockBackendList(page, [
      {
        id: 'jade-tipi-org~dev~test~grp~analytics',
        collection: 'grp',
        name: 'Analytics',
        description: 'Analytics group',
        permissions: {},
      },
    ]);

    await page.goto('/admin/groups');
    await expect(page.getByTestId('admin-groups-list')).toBeVisible();
    await expect(page.getByText('Analytics')).toBeVisible();
    await expect(page.getByText('jade-tipi-org~dev~test~grp~analytics')).toBeVisible();
  });
});
