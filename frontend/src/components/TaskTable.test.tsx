import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { task } from '@/test/fixtures'
import { TaskTable } from './TaskTable'

const page = (items = [task({ id: '1', identifier: 'WEB-1', title: 'First' }), task({ id: '2', identifier: 'WEB-2', title: 'Second', overdue: true, dueDate: '2020-01-01' })]) => ({
  items, page: 0, size: 25, totalItems: 60, totalPages: 3,
})

const setup = (over: Partial<Parameters<typeof TaskTable>[0]> = {}) => {
  const onSort = vi.fn()
  const onPage = vi.fn()
  render(
    <MemoryRouter>
      <TaskTable page={page()} sort="key" dir="asc" onSort={onSort} onPage={onPage} {...over} />
    </MemoryRouter>,
  )
  return { onSort, onPage }
}

describe('TaskTable', () => {
  it('renders one row per task with identifier, status and priority', () => {
    setup()
    const row = screen.getByTestId('row-WEB-2')
    expect(within(row).getByText('Second')).toBeInTheDocument()
    expect(within(row).getByText('To Do')).toBeInTheDocument()
    expect(within(row).getByText('Medium')).toBeInTheDocument()
    expect(within(row).getByText(/Overdue/)).toBeInTheDocument()
  })

  it('reports sort changes and marks the active column', async () => {
    const { onSort } = setup()
    await userEvent.click(screen.getByRole('button', { name: 'Priority' }))
    expect(onSort).toHaveBeenCalledWith('priority')
    expect(screen.getByRole('columnheader', { name: /ID/ })).toHaveAttribute('aria-sort', 'ascending')
  })

  it('paginates', async () => {
    const { onPage } = setup()
    expect(screen.getByText('1–2 of 60')).toBeInTheDocument()
    expect(screen.getByLabelText('Previous page')).toBeDisabled()
    await userEvent.click(screen.getByLabelText('Next page'))
    expect(onPage).toHaveBeenCalledWith(1)
  })

  it('shows an empty message', () => {
    setup({ page: { items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 } })
    expect(screen.getByText('No tasks match these filters.')).toBeInTheDocument()
  })
})
