import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmProvider, useConfirm } from './ui/confirm'

function Harness({ onResult }: { onResult: (v: boolean) => void }) {
  const confirm = useConfirm()
  return <button onClick={async () => onResult(await confirm({ title: 'Delete WEB-1?', description: 'Cannot be undone' }))}>delete</button>
}

describe('destructive confirmation', () => {
  it('resolves true only after the user confirms', async () => {
    const onResult = vi.fn()
    render(<ConfirmProvider><Harness onResult={onResult} /></ConfirmProvider>)
    await userEvent.click(screen.getByText('delete'))
    expect(await screen.findByRole('dialog')).toHaveTextContent('Delete WEB-1?')
    expect(onResult).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
    expect(onResult).toHaveBeenCalledWith(true)
  })

  it('resolves false on cancel', async () => {
    const onResult = vi.fn()
    render(<ConfirmProvider><Harness onResult={onResult} /></ConfirmProvider>)
    await userEvent.click(screen.getByText('delete'))
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }))
    expect(onResult).toHaveBeenCalledWith(false)
  })
})
