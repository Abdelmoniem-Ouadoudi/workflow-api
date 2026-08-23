import { useState } from 'react'
import type { Project } from './api/types'
import { Board } from './components/Board'
import { ProjectList } from './components/ProjectList'

/**
 * Two screens, switched by state. No router yet: with one route pair it would be a
 * dependency and a concept for no gain. Recorded in docs/BACKLOG.md.
 */
export function App() {
  const [project, setProject] = useState<Project | null>(null)

  return project === null ? (
    <ProjectList onOpen={setProject} />
  ) : (
    <Board project={project} onLeave={() => setProject(null)} />
  )
}
