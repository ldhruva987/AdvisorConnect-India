import { Link } from 'react-router-dom'
import { SECTOR_LABELS } from '@/lib/sectors'
import type { AdvisorSectorEnum } from '@/lib/sectors'

/**
 * The subset of sectors the footer surfaces. Enum values, not labels: ExplorePage
 * parses `?sector=` with `isAdvisorSectorEnum`, so the old `?sector=Mental+Health`
 * links here matched nothing and landed on an unfiltered list.
 */
const FOOTER_SECTORS: AdvisorSectorEnum[] = ['CAREER', 'FINANCE', 'MENTAL_HEALTH', 'RELATIONSHIPS']

export function Footer() {
  return (
    <footer className="bg-ink-900 text-white/70 py-12">
      <div className="max-w-7xl mx-auto px-6">
        <div className="grid md:grid-cols-4 gap-8 mb-8">
          <div>
            <div className="font-heading font-medium text-white text-lg mb-3">AdvisorConnect</div>
            <p className="text-sm leading-relaxed">Free expert advice from verified professionals. Your privacy is our promise.</p>
          </div>
          {[
            { title: 'Platform', links: [{ label: 'Explore Advisors', to: '/explore' }, { label: 'Become an Advisor', to: '/onboarding' }, { label: 'Pricing', to: '/#pricing' }] },
            { title: 'Sectors', links: FOOTER_SECTORS.map((value) => ({ label: SECTOR_LABELS[value], to: `/explore?sector=${value}` })) },
            { title: 'Legal', links: [{ label: 'Privacy Policy', to: '/privacy' }, { label: 'Terms of Service', to: '/terms' }, { label: 'Advisor Agreement', to: '/advisor-terms' }] },
          ].map((col) => (
            <div key={col.title}>
              <h3 className="text-sm font-semibold text-white mb-3">{col.title}</h3>
              <ul className="space-y-2">
                {col.links.map((l) => (
                  <li key={l.to}>
                    <Link to={l.to} className="text-sm hover:text-white transition-colors">{l.label}</Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="border-t border-ink-800 pt-6 flex flex-col md:flex-row items-center justify-between gap-3 text-sm">
          <span>© {new Date().getFullYear()} AdvisorConnect. All rights reserved.</span>
          <span className="text-white/60">Your privacy is our priority — real identities never shared.</span>
        </div>
      </div>
    </footer>
  )
}
