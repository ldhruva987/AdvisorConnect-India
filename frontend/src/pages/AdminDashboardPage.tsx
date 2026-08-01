import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  BarChart2, FileText, GraduationCap, Users, Flag, Settings,
  ArrowLeft, FileIcon, CheckCircle, XCircle, AlertTriangle,
  Eye, Save,
} from 'lucide-react'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Badge, StatusBadge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import { Card, CardHeader } from '@/shared/components/ui/Card'

type AdminPage = 'dashboard' | 'applications' | 'detail'
type AdminNav = 'dashboard' | 'applications' | 'advisors' | 'users' | 'reports' | 'settings'
type FilterStatus = 'All' | 'PENDING' | 'APPROVED' | 'REJECTED'

interface MockApplication {
  id: string
  username: string
  realName: string
  sector: string
  qual: string
  date: string
  docCount: number
  status: string
  color: string
}

const MOCK_APPLICATIONS: MockApplication[] = [
  { id: '1', username: '@CareerAdvisorPro', realName: 'Alexandra Chen', sector: 'Career', qual: "Master's in HR", date: 'Mar 14, 2026', docCount: 3, status: 'PENDING', color: '#8a3f24' },
  { id: '2', username: '@ZenFinancePro', realName: 'Marcus Johnson', sector: 'Finance', qual: 'CFA Certified', date: 'Mar 13, 2026', docCount: 4, status: 'PENDING', color: '#784f00' },
  { id: '3', username: '@DrResilience', realName: 'Priya Sharma', sector: 'Mental Health', qual: 'PhD Psychology', date: 'Mar 12, 2026', docCount: 3, status: 'APPROVED', color: '#516000' },
  { id: '4', username: '@LifeGuideNow', realName: 'Tom Bradley', sector: 'Relationships', qual: 'MA Counselling', date: 'Mar 11, 2026', docCount: 2, status: 'PENDING', color: '#005e8f' },
  { id: '5', username: '@MoneyWiseAdv', realName: 'Sarah Williams', sector: 'Finance', qual: 'CFP Certified', date: 'Mar 10, 2026', docCount: 4, status: 'REJECTED', color: '#006970' },
  { id: '6', username: '@HolisticCoach', realName: 'Emma Davis', sector: 'Life Coaching', qual: 'ICF PCC', date: 'Mar 9, 2026', docCount: 3, status: 'APPROVED', color: '#4d4f94' },
  { id: '7', username: '@RelationshipMD', realName: 'David Park', sector: 'Relationships', qual: 'PhD Sociology', date: 'Mar 8, 2026', docCount: 3, status: 'PENDING', color: '#73417e' },
]

const ADMIN_STATS = [
  { label: 'Pending Applications', value: '12', delta: '+3 today', color: 'text-warn-600', bg: 'bg-warn-100' },
  { label: 'Active Advisors', value: '248', delta: '+5 this week', color: 'text-pine-600', bg: 'bg-pine-100' },
  { label: 'Total Users', value: '14,820', delta: '+120 today', color: 'text-oxblood-700', bg: 'bg-oxblood-50' },
  { label: 'Platform Revenue', value: '$48,290', delta: '+$2,100 today', color: 'text-ink-700', bg: 'bg-ink-100' },
]

const ACTIVITY_FEED = [
  { id: '1', icon: '✅', iconBg: 'bg-pine-100', text: '@DrResilience was approved as a Mental Health advisor', time: '5 min ago' },
  { id: '2', icon: '📝', iconBg: 'bg-oxblood-50', text: 'New application from @RelationshipMD — Relationships', time: '12 min ago' },
  { id: '3', icon: '🚩', iconBg: 'bg-danger-100', text: 'User @AnonymousUser123 was flagged for inappropriate content', time: '1 hour ago' },
  { id: '4', icon: '💰', iconBg: 'bg-pine-100', text: '$69 video session completed — @AnxiousAnna → @MindfulRohan', time: '2 hours ago' },
  { id: '5', icon: '❌', iconBg: 'bg-danger-100', text: '@MoneyWiseAdv application was rejected — incomplete docs', time: '3 hours ago' },
]

const NAV_ITEMS: { id: AdminNav; icon: React.ReactNode; label: string; badge?: number }[] = [
  { id: 'dashboard', icon: <BarChart2 className="w-4 h-4" />, label: 'Dashboard' },
  { id: 'applications', icon: <FileText className="w-4 h-4" />, label: 'Applications', badge: 12 },
  { id: 'advisors', icon: <GraduationCap className="w-4 h-4" />, label: 'All Advisors' },
  { id: 'users', icon: <Users className="w-4 h-4" />, label: 'All Users' },
  { id: 'reports', icon: <Flag className="w-4 h-4" />, label: 'Reports', badge: 3 },
  { id: 'settings', icon: <Settings className="w-4 h-4" />, label: 'Settings' },
]

export function AdminDashboardPage() {
  const [adminNav, setAdminNav] = useState<AdminNav>('dashboard')
  const [adminPage, setAdminPage] = useState<AdminPage>('dashboard')
  const [filterStatus, setFilterStatus] = useState<FilterStatus>('All')
  const [adminNotes, setAdminNotes] = useState('')

  const handleNavClick = (id: AdminNav) => {
    setAdminNav(id)
    if (id === 'dashboard') setAdminPage('dashboard')
    if (id === 'applications') setAdminPage('applications')
  }

  const filteredApps = MOCK_APPLICATIONS.filter((a) =>
    filterStatus === 'All' ? true : a.status === filterStatus
  )

  const detailApp = MOCK_APPLICATIONS[0]

  return (
    <div className="flex h-screen bg-ink-50 overflow-hidden">
      {/* Dark sidebar */}
      <aside className="w-64 bg-ink-900 text-white flex flex-col flex-shrink-0">
        {/* Header */}
        <div className="p-5 border-b border-white/10">
          <p className="text-xs font-semibold text-white/60 uppercase tracking-wider mb-1">Admin Panel</p>
          <p className="font-heading font-medium text-lg">AdvisorConnect</p>
        </div>

        {/* Nav */}
        <nav className="flex-1 p-3 space-y-1">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.id}
              onClick={() => handleNavClick(item.id)}
              className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                adminNav === item.id
                  ? 'bg-white/10 text-white font-semibold'
                  : 'text-white/60 hover:bg-white/5 hover:text-white'
              }`}
            >
              <div className="flex items-center gap-2.5">
                {item.icon}
                {item.label}
              </div>
              {item.badge && (
                <span className="w-5 h-5 bg-oxblood-600 text-white text-xs font-bold rounded-full flex items-center justify-center">
                  {item.badge}
                </span>
              )}
            </button>
          ))}
        </nav>

        {/* Exit */}
        <div className="p-4 border-t border-white/10">
          <Link
            to="/"
            className="flex items-center gap-2 text-sm text-white/60 hover:text-white transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Exit admin
          </Link>
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 overflow-y-auto">
        {/* DASHBOARD VIEW */}
        {adminPage === 'dashboard' && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-6">
              <div>
                <h1 className="font-heading font-medium text-2xl text-ink-900">Admin Overview</h1>
                <p className="text-ink-500 text-sm mt-0.5">Monday, March 16, 2026</p>
              </div>
            </div>

            {/* Stat cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              {ADMIN_STATS.map((stat) => (
                <div key={stat.label} className={`${stat.bg} rounded-xl p-5`}>
                  <p className="text-xs font-semibold text-ink-500 uppercase tracking-wider mb-1">{stat.label}</p>
                  <p className={`font-heading font-medium text-2xl ${stat.color}`}>{stat.value}</p>
                  <p className="text-xs text-ink-500 mt-1">{stat.delta}</p>
                </div>
              ))}
            </div>

            <div className="grid lg:grid-cols-3 gap-5">
              {/* Pending applications table */}
              <div className="lg:col-span-2">
                <Card padding="none">
                  <CardHeader>
                    <h2 className="font-semibold text-ink-900">Pending Applications</h2>
                    <Badge variant="pending">12 pending</Badge>
                  </CardHeader>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-ink-100 bg-ink-50">
                          <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Applicant</th>
                          <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Sector</th>
                          <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Applied</th>
                          <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Status</th>
                          <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-ink-100">
                        {MOCK_APPLICATIONS.slice(0, 5).map((app) => (
                          <tr key={app.id} className="hover:bg-ink-50 transition-colors">
                            <td className="px-4 py-3">
                              <div className="flex items-center gap-2">
                                <Avatar username={app.username} color={app.color} size="sm" />
                                <div>
                                  <p className="font-medium text-ink-900">{app.username}</p>
                                  <p className="text-xs text-ink-400 blur-sm select-none">{app.realName}</p>
                                </div>
                              </div>
                            </td>
                            <td className="px-4 py-3 text-ink-600">{app.sector}</td>
                            <td className="px-4 py-3 text-ink-400 text-xs">{app.date}</td>
                            <td className="px-4 py-3">
                              <StatusBadge status={app.status} />
                            </td>
                            <td className="px-4 py-3">
                              <button
                                onClick={() => { setAdminPage('detail'); setAdminNav('applications') }}
                                className="text-oxblood-700 font-semibold text-xs hover:text-oxblood-600 transition-colors"
                              >
                                Review →
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </Card>
              </div>

              {/* Activity feed */}
              <div>
                <Card padding="none">
                  <CardHeader>
                    <h2 className="font-semibold text-ink-900">Recent Activity</h2>
                  </CardHeader>
                  <div className="divide-y divide-ink-100">
                    {ACTIVITY_FEED.map((item) => (
                      <div key={item.id} className="flex items-start gap-3 p-4">
                        <div className={`w-8 h-8 rounded-lg ${item.iconBg} flex items-center justify-center text-sm flex-shrink-0`}>
                          {item.icon}
                        </div>
                        <div>
                          <p className="text-xs text-ink-700 leading-relaxed">{item.text}</p>
                          <p className="text-xs text-ink-400 mt-0.5">{item.time}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </Card>
              </div>
            </div>
          </div>
        )}

        {/* APPLICATIONS VIEW */}
        {adminPage === 'applications' && adminNav === 'applications' && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-6">
              <h1 className="font-heading font-medium text-2xl text-ink-900">Applications</h1>
            </div>

            {/* Filter buttons */}
            <div className="flex gap-2 mb-5">
              {(['All', 'PENDING', 'APPROVED', 'REJECTED'] as FilterStatus[]).map((f) => (
                <button
                  key={f}
                  onClick={() => setFilterStatus(f)}
                  className={`px-4 py-2 rounded-lg text-sm font-medium border transition-all ${
                    filterStatus === f
                      ? 'bg-ink-900 text-white border-ink-900'
                      : 'bg-white text-ink-600 border-ink-200 hover:border-ink-400'
                  }`}
                >
                  {f === 'All' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}
                  {f === 'PENDING' && <span className="ml-1.5 bg-warn-100 text-warn-600 text-xs font-bold px-1.5 py-0.5 rounded-full">4</span>}
                </button>
              ))}
            </div>

            <Card padding="none">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-ink-100 bg-ink-50">
                      <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Applicant</th>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Sector</th>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Qualification</th>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Applied</th>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Docs</th>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Status</th>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-ink-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-100">
                    {filteredApps.map((app) => (
                      <tr key={app.id} className="hover:bg-ink-50 transition-colors">
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <Avatar username={app.username} color={app.color} size="sm" />
                            <div>
                              <p className="font-medium text-ink-900">{app.username}</p>
                              <p className="text-xs text-ink-400 blur-sm select-none">{app.realName}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-ink-600">{app.sector}</td>
                        <td className="px-4 py-3 text-ink-600 text-xs">{app.qual}</td>
                        <td className="px-4 py-3 text-ink-400 text-xs">{app.date}</td>
                        <td className="px-4 py-3">
                          <span className="bg-ink-100 text-ink-600 text-xs font-semibold px-2 py-0.5 rounded-full">
                            {app.docCount} docs
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={app.status} />
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <button
                              onClick={() => setAdminPage('detail')}
                              className="text-oxblood-700 font-semibold text-xs hover:text-oxblood-600 transition-colors flex items-center gap-1"
                            >
                              <Eye className="w-3 h-3" />
                              Review
                            </button>
                            {app.status === 'PENDING' && (
                              <>
                                <button className="text-pine-600 font-semibold text-xs hover:text-pine-600/80 transition-colors flex items-center gap-1">
                                  <CheckCircle className="w-3 h-3" />
                                  Approve
                                </button>
                                <button className="text-danger-600 font-semibold text-xs hover:text-danger-600/80 transition-colors flex items-center gap-1">
                                  <XCircle className="w-3 h-3" />
                                  Reject
                                </button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </div>
        )}

        {/* DETAIL VIEW */}
        {adminPage === 'detail' && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-6">
              <button
                onClick={() => setAdminPage('applications')}
                className="flex items-center gap-2 text-ink-500 hover:text-ink-900 transition-colors text-sm font-medium"
              >
                <ArrowLeft className="w-4 h-4" />
                Back to Applications
              </button>
              <StatusBadge status="PENDING" />
            </div>

            <div className="grid lg:grid-cols-2 gap-5">
              {/* LEFT COLUMN */}
              <div className="space-y-5">
                {/* Public profile card */}
                <Card>
                  <h3 className="font-heading font-semibold text-ink-900 mb-4">Public Profile</h3>
                  <div className="flex items-start gap-3 mb-4">
                    <Avatar username={detailApp.username} color={detailApp.color} size="lg" />
                    <div>
                      <p className="font-heading font-semibold text-ink-900">{detailApp.username}</p>
                      <p className="text-sm text-ink-500">Career Development Consultant</p>
                      <div className="flex gap-1.5 mt-1.5 flex-wrap">
                        {['Career', 'Leadership', 'Negotiation'].map((tag) => (
                          <Badge key={tag} variant="brand">{tag}</Badge>
                        ))}
                      </div>
                    </div>
                  </div>
                  <p className="text-sm text-ink-600 leading-relaxed">
                    Experienced career coach specializing in mid-career transitions, executive presence, and salary negotiation. Worked with 200+ professionals across Fortune 500 companies.
                  </p>
                </Card>

                {/* Private identity card */}
                <Card className="border-danger-600/30">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="font-heading font-semibold text-ink-900">Identity Verification</h3>
                    <Badge variant="danger">Admin Eyes Only</Badge>
                  </div>
                  <table className="w-full text-sm">
                    <tbody className="divide-y divide-ink-100">
                      {[
                        { label: 'Legal Name', value: detailApp.realName, blur: true },
                        { label: 'Date of Birth', value: '15/03/1988', blur: true },
                        { label: 'Email', value: 'alex.chen@email.com', blur: true },
                        { label: 'Address', value: '245 Park Ave, New York, NY 10167', blur: true },
                      ].map((row) => (
                        <tr key={row.label}>
                          <td className="py-2.5 pr-4 text-xs font-semibold text-ink-500 uppercase tracking-wider whitespace-nowrap">{row.label}</td>
                          <td className={`py-2.5 text-ink-800 ${row.blur ? 'blur-sm select-none' : ''}`}>{row.value}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </Card>

                {/* Credentials card */}
                <Card>
                  <h3 className="font-heading font-semibold text-ink-900 mb-4">Credentials</h3>
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between">
                      <span className="text-ink-400">Qualification</span>
                      <span className="font-medium text-ink-800">{detailApp.qual}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-ink-400">Experience</span>
                      <span className="font-medium text-ink-800">8 years</span>
                    </div>
                    <div className="mt-3 pt-3 border-t border-ink-100">
                      <p className="text-xs font-semibold text-ink-500 mb-1 uppercase tracking-wider">Previous Work</p>
                      <p className="text-sm text-ink-600 leading-relaxed">
                        Former Head of Talent at TechCorp Inc. (2018–2023). Coached 200+ employees on career development. Published author of "The Career Pivot Playbook."
                      </p>
                    </div>
                  </div>
                </Card>

                {/* Admin notes */}
                <Card>
                  <h3 className="font-heading font-semibold text-ink-900 mb-3">Admin Notes</h3>
                  <textarea
                    value={adminNotes}
                    onChange={(e) => setAdminNotes(e.target.value)}
                    placeholder="Add internal notes about this application..."
                    rows={3}
                    className="input-base resize-none mb-3"
                  />
                  <Button variant="outline" size="sm" onClick={() => {}}>
                    <Save className="w-4 h-4" />
                    Save Notes
                  </Button>
                </Card>
              </div>

              {/* RIGHT COLUMN */}
              <div className="space-y-5">
                {/* Documents card */}
                <Card>
                  <h3 className="font-heading font-semibold text-ink-900 mb-4">Submitted Documents</h3>
                  <div className="space-y-3">
                    {[
                      { name: "master_degree_certificate.pdf", size: "2.4 MB", date: "Mar 14, 2026", type: "pdf" },
                      { name: "professional_license.jpg", size: "1.1 MB", date: "Mar 14, 2026", type: "img" },
                      { name: "passport_front.jpg", size: "0.8 MB", date: "Mar 14, 2026", type: "img" },
                    ].map((doc) => (
                      <div key={doc.name} className="flex items-center gap-3 p-3 bg-ink-50 rounded-xl">
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${
                          doc.type === 'pdf' ? 'bg-danger-100' : 'bg-oxblood-50'
                        }`}>
                          <FileIcon className={`w-4 h-4 ${doc.type === 'pdf' ? 'text-danger-600' : 'text-oxblood-700'}`} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-xs font-semibold text-ink-800 truncate">{doc.name}</p>
                          <p className="text-xs text-ink-400">{doc.size} · {doc.date}</p>
                        </div>
                        <Button variant="ghost" size="sm">
                          <Eye className="w-3 h-3" />
                          View
                        </Button>
                      </div>
                    ))}
                  </div>
                </Card>

                {/* Document preview placeholder */}
                <Card className="bg-ink-100 border-ink-200 flex flex-col items-center justify-center min-h-48">
                  <FileIcon className="w-12 h-12 text-ink-300 mb-3" />
                  <p className="text-sm text-ink-400 font-medium">Select a document to preview</p>
                  <p className="text-xs text-ink-300 mt-1">Click "View" on any document above</p>
                </Card>

                {/* Decision card */}
                <Card>
                  <h3 className="font-heading font-semibold text-ink-900 mb-4">Application Decision</h3>
                  <div className="space-y-3">
                    <Button variant="success" fullWidth className="justify-center">
                      <CheckCircle className="w-4 h-4" />
                      Approve Application
                    </Button>
                    <Button
                      variant="ghost"
                      fullWidth
                      className="justify-center border border-warn-600/30 text-warn-600 hover:bg-warn-100 hover:text-warn-600"
                    >
                      <AlertTriangle className="w-4 h-4" />
                      Request More Information
                    </Button>
                    <Button variant="danger" fullWidth className="justify-center">
                      <XCircle className="w-4 h-4" />
                      Reject Application
                    </Button>
                  </div>
                </Card>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
