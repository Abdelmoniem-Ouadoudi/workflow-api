import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { CountByLabel, Dashboard as DashboardData } from '../api/types'
import type { Session } from '../auth/session'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { WhoAmI } from './WhoAmI'

interface Props {
  session: Session
  onLeave: () => void
  onSignOut: () => void
}

/**
 * What the AI layer has actually done, in numbers.
 *
 * Bars drawn in CSS rather than with a charting library. Five distributions and a percentage do
 * not justify a dependency, and a chart library would be the first thing in this project that
 * could not be explained line by line.
 */
export function Dashboard({ session, onLeave, onSignOut }: Props) {
  const [data, setData] = useState<DashboardData | null>(null)
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    api
      .dashboard()
      .then((loaded) => {
        if (!cancelled) setData(loaded)
      })
      .catch((error) => {
        if (!cancelled && error instanceof ApiError) {
          setNotice({ tone: 'stop', message: error.message })
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) return <p className="page__loading page">Reading the numbers…</p>

  return (
    <main className="page">
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      <header className="masthead">
        <div className="masthead__top">
          <button type="button" className="panel__back" onClick={onLeave}>
            ← Projects
          </button>
          <WhoAmI session={session} onSignOut={onSignOut} />
        </div>
        <h1 className="masthead__title">Insights</h1>
        <p className="masthead__sub">
          What the classifier has read, and how often it was right.
        </p>
        <div className="masthead__rail" aria-hidden="true" />
      </header>

      {data && (
        <>
          <section className="figures">
            <Figure label="Issues" value={String(data.totalIssues)} />
            <Figure label="Read by the AI" value={String(data.classifiedIssues)} />
            <Figure label="Awaiting review" value={String(data.awaitingReview)} />
            <Figure
              label="Agreement"
              // Null and zero are different claims. Zero would say the AI is always wrong;
              // null says nobody has judged one yet, which is the honest state on a new install.
              value={data.aiAgreementRate === null ? '—' : `${data.aiAgreementRate}%`}
              note={
                data.aiAgreementRate === null
                  ? 'Nothing judged yet'
                  : 'Applied or accepted, out of everything decided'
              }
              strong
            />
          </section>

          <div className="charts">
            <Chart title="By type" rows={data.byType} />
            <Chart title="By priority" rows={data.byPriority} />
            <Chart title="By status" rows={data.byStatus} />
            <Chart
              title="Team load"
              rows={data.byTeam}
              // Said plainly rather than left to be discovered: this axis is the model's opinion,
              // not a field anybody filled in. An issue has no team.
              note="Inferred by the AI. There is no team field on an issue."
            />
            <Chart title="Effort" rows={data.byEffort} note="Inferred by the AI." />
          </div>
        </>
      )}
    </main>
  )
}

function Figure({
  label,
  value,
  note,
  strong,
}: {
  label: string
  value: string
  note?: string
  strong?: boolean
}) {
  return (
    <div className={`figure${strong ? ' figure--strong' : ''}`}>
      <span className="figure__label">{label}</span>
      <span className="figure__value">{value}</span>
      {note && <span className="figure__note">{note}</span>}
    </div>
  )
}

function Chart({ title, rows, note }: { title: string; rows: CountByLabel[]; note?: string }) {
  // Bars are drawn relative to the largest one, not to the total. Relative to the total, a healthy
  // spread of six categories renders as six slivers and says nothing.
  const largest = rows.reduce((max, row) => Math.max(max, row.count), 0)

  return (
    <section className="chart">
      <h2 className="chart__title">{title}</h2>
      {note && <p className="chart__note">{note}</p>}

      {rows.length === 0 && <p className="chart__empty">Nothing yet.</p>}

      <ul className="chart__rows">
        {rows.map((row) => (
          <li className="chart__row" key={row.label}>
            <span className="chart__label">{row.label.toLowerCase()}</span>
            <span className="chart__track">
              <span
                className="chart__bar"
                style={{ width: largest === 0 ? '0%' : `${(row.count / largest) * 100}%` }}
              />
            </span>
            <span className="chart__count">{row.count}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
