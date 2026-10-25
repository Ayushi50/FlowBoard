import { useOutletContext } from 'react-router-dom'
import type { Project } from '@/lib/types'

export const useCurrentProject = () => useOutletContext<Project>()
