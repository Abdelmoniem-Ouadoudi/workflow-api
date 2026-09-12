import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { CountByLabel, Dashboard as DashboardData } from '../api/types'
import { Notice } from './Notice'
import type { NoticeState } from './Notice'
import { Page, PageHead } from './ui'

/**
 * What the AI layer has actually done, in numbers.
 *
 * Bars drawn in CSS rather than with a charting library. Five distributions and a percentage do
 * not justify a dependency, and a chart library would be the first thing in this project that
 * could not be explained line by line.
 */
export function Dashboard() {
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

  return (
    <Page crumbs={[{ label: 'Insights' }]}>
      <PageHead
        title="Insights"
        sub="What the classifier has read, and how often it was right — across the projects you are on."
      />
      <Notice notice={notice} onDismiss={() => setNotice(null)} />

      {loading && <p className="loading">Reading the numbers…</p>}

      {data && (
        <>
          <section className="stats">
            <Stat label="Issues" value={String(data.totalIssues)} />
            <Stat label="Read by the AI" value={String(data.classifiedIssues)} />
            <Stat label="Awaiting review" value={String(data.awaitingReview)} />
            <Stat
              // Null and zero are different claims. Zero would say the AI is always wrong;
              // null says nobody has judged one yet, which is the honest state on a new install.
              label={
                data.aiAgreementRate === null
                  ? 'AI agreement — nothing judged yet'
                  : 'AI agreement rate'
              }
              value={data.aiAgreementRate === null ? '—' : `${data.aiAgreementRate}%`}
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
    </Page>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat">
      <p className="stat__value">{value}</p>
      <p className="stat__label">{label}</p>
    </div>
  )
}

function Chart({ title, rows, note }: { title: string; rows: CountByLabel[]; note?: string }) {
  // Bars are drawn relative to the largest one, not to the total. Relative to the total, a healthy
  // spread of six categories renders as six slivers and says nothing.
  const largest = rows.reduce((max, row) => Math.max(max, row.count), 0)

  return (
    <section className="card">
      <h2 className="card__title">{title}</h2>
      {note && <p className="chart__note">{note}</p>}

      {rows.length === 0 && <p className="empty">Nothing yet.</p>}

      <ul className="chart__rows">
        {rows.map((row) => (
          <li className="chart__row" key={row.label}>
            <span className="chart__label">{row.label.replace('_', ' ').toLowerCase()}</span>
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
