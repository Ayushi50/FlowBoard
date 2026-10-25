import { expect, test, type Locator, type Page } from '@playwright/test'

const unique = () => Math.random().toString(36).slice(2, 8)

/** dnd-kit listens to pointer events, so drive the mouse in small steps instead of using dragTo(). */
async function drag(page: Page, source: Locator, target: Locator) {
  const s = (await source.boundingBox())!
  const t = (await target.boundingBox())!
  await page.mouse.move(s.x + s.width / 2, s.y + s.height / 2)
  await page.mouse.down()
  await page.mouse.move(s.x + s.width / 2 + 12, s.y + s.height / 2 + 12, { steps: 4 })
  await page.mouse.move(t.x + t.width / 2, t.y + 40, { steps: 20 })
  await page.mouse.up()
}

test('register, create a project, manage tasks across views, and persist everything', async ({ page }) => {
  const id = unique()
  const email = `e2e-${id}@test.dev`

  // --- sign up (creates the workspace and logs in)
  await page.goto('/register')
  await page.getByLabel('Full name').fill('Erin E2E')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel(/^Password/).fill('Password123!')
  await page.getByLabel('Workspace name').fill(`Workspace ${id}`)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: /Good (morning|afternoon|evening), Erin/ })).toBeVisible()

  // --- empty state invites the first project
  await expect(page.getByText('Create your first project')).toBeVisible()
  await page.getByRole('button', { name: 'New project' }).first().click()
  await page.getByLabel('Name', { exact: true }).fill('Checkout Revamp')
  await expect(page.getByLabel('Key')).toHaveValue('CR')
  await page.getByRole('button', { name: 'Create project' }).click()
  await expect(page.getByRole('heading', { name: 'Checkout Revamp' })).toBeVisible()
  await expect(page).toHaveURL(/\/board$/)

  // --- create tasks directly from columns
  await page.getByRole('button', { name: 'Add task to To Do' }).click()
  await page.getByLabel('New task in To Do').fill('Design payment form')
  await page.keyboard.press('Enter')
  await expect(page.getByTestId('card-CR-1')).toBeVisible()
  await page.getByLabel('New task in To Do').fill('Wire up Stripe webhook')
  await page.keyboard.press('Enter')
  await expect(page.getByTestId('card-CR-2')).toBeVisible()

  // --- drag a card to In Progress and confirm it survives a reload
  await drag(page, page.getByTestId('card-CR-1'), page.getByRole('region', { name: 'In Progress column' }))
  await expect(page.getByRole('region', { name: 'In Progress column' }).getByTestId('card-CR-1')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: 'In Progress column' }).getByTestId('card-CR-1')).toBeVisible()
  await expect(page.getByRole('region', { name: 'To Do column' }).getByTestId('card-CR-2')).toBeVisible()

  // --- edit in the detail panel: priority, assignee, comment
  await page.getByTestId('card-CR-1').click()
  const panel = page.getByRole('dialog')
  await expect(panel.getByLabel('Task title')).toHaveValue('Design payment form')
  await panel.getByLabel('Priority').selectOption('URGENT')
  await panel.getByLabel('Assignee').selectOption({ label: 'Erin E2E' })
  await panel.getByLabel('Comment', { exact: true }).fill('Kicking this off today')
  await panel.getByRole('button', { name: 'Comment', exact: true }).click()
  await expect(panel.getByText('Kicking this off today')).toBeVisible()
  await panel.getByRole('button', { name: 'Activity' }).click()
  await expect(panel.getByText(/changed priority of CR-1 from NONE to URGENT/)).toBeVisible()
  await panel.getByRole('button', { name: 'Close panel' }).click()

  // --- the List view shows the very same data
  await page.getByRole('link', { name: 'List' }).click()
  const row = page.getByTestId('row-CR-1')
  await expect(row).toContainText('In Progress')
  await expect(row).toContainText('Urgent')
  await expect(row).toContainText('Erin E2E')
  await expect(page.getByTestId('row-CR-2')).toContainText('To Do')

  // --- filtering is applied by the server
  await page.getByLabel('Search tasks').fill('Stripe')
  await expect(page.getByTestId('row-CR-2')).toBeVisible()
  await expect(page.getByTestId('row-CR-1')).toHaveCount(0)

  // --- deleting a project asks for confirmation first
  await page.getByRole('button', { name: 'Project actions' }).click()
  await page.getByRole('menuitem', { name: 'Delete project' }).click()
  await expect(page.getByRole('dialog')).toContainText('Delete Checkout Revamp?')
  await page.getByRole('button', { name: 'Cancel' }).click()
  await expect(page.getByRole('heading', { name: 'Checkout Revamp' })).toBeVisible()
})

test('a second user cannot open the first user\'s workspace by URL', async ({ page, browser }) => {
  const id = unique()
  await page.goto('/register')
  await page.getByLabel('Full name').fill('Owner')
  await page.getByLabel('Email').fill(`owner-${id}@test.dev`)
  await page.getByLabel(/^Password/).fill('Password123!')
  await page.getByLabel('Workspace name').fill(`Private ${id}`)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: /Good/ })).toBeVisible()
  const privateUrl = page.url()

  const other = await (await browser.newContext()).newPage()
  const base = new URL(privateUrl).origin
  await other.goto(`${base}/register`)
  await other.getByLabel('Full name').fill('Intruder')
  await other.getByLabel('Email').fill(`intruder-${id}@test.dev`)
  await other.getByLabel(/^Password/).fill('Password123!')
  await other.getByLabel('Workspace name').fill('Mine')
  await other.getByRole('button', { name: 'Create account' }).click()
  await expect(other.getByRole('heading', { name: /Good/ })).toBeVisible()
  await other.goto(privateUrl)
  await expect(other.getByText(`Private ${id}`)).toHaveCount(0)
  await expect(other).not.toHaveURL(privateUrl)
})
