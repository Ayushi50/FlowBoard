import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '@/lib/auth'
import { getToken, setToken } from '@/lib/api'
import { user } from '@/test/fixtures'
import { AuthPage } from './AuthPage'

const renderLogin = (mode: 'login' | 'register' = 'login') =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<AuthPage mode={mode} />} />
            <Route path="/" element={<p>home</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
})

describe('AuthPage', () => {
  it('logs in, stores the token and navigates home', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ token: 'jwt-1', expiresInSeconds: 60, user: user() }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    renderLogin()
    await userEvent.type(screen.getByLabelText('Email'), 'ava@test.dev')
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    await screen.findByText('home')
    expect(getToken()).toBe('jwt-1')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ email: 'ava@test.dev', password: 'Password123!' })
  })

  it('shows the server message for bad credentials and keeps the user on the page', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: 'Invalid email or password' }), { status: 401 })))
    renderLogin()
    await userEvent.type(screen.getByLabelText('Email'), 'ava@test.dev')
    await userEvent.type(screen.getByLabelText('Password'), 'nope')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.queryByText('home')).not.toBeInTheDocument()
    expect(getToken()).toBeNull()
  })

  it('renders field-level validation errors on sign up', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'VALIDATION_FAILED', message: 'x', fieldErrors: { password: 'must be 8-72 characters' } }), { status: 400 })))
    renderLogin('register')
    await userEvent.type(screen.getByLabelText('Full name'), 'Ava')
    await userEvent.type(screen.getByLabelText('Email'), 'ava@test.dev')
    await userEvent.type(screen.getByLabelText(/^Password/), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(screen.getByText('must be 8-72 characters')).toBeInTheDocument())
  })
})
