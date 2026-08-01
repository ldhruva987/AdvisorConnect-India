import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle, Upload, AlertTriangle } from 'lucide-react'
import { Button } from '@/shared/components/ui/Button'
import { Input, Textarea } from '@/shared/components/ui/Input'
import { Modal } from '@/shared/components/ui/Modal'
import { Navbar } from '@/shared/components/layout/Navbar'

const STEPS = ['Public Profile', 'Credentials', 'Identity Verification', 'Review & Submit']

const SECTORS = [
  'Career', 'Relationships', 'Finance', 'Mental Health',
  'Life Coaching', 'Parenting', 'Health & Wellness', 'Business',
]

interface FormData {
  username: string
  title: string
  bio: string
  selectedSectors: string[]
  languages: string
  qualification: string
  fieldOfStudy: string
  experienceYears: string
  previousWork: string
  legalFirstName: string
  legalLastName: string
  dob: string
  streetAddress: string
  city: string
  state: string
  zip: string
  country: string
  idType: string
  consentData: boolean
  consentTerms: boolean
}

export function OnboardingPage() {
  const navigate = useNavigate()
  const [currentStep, setCurrentStep] = useState(0)
  const [successOpen, setSuccessOpen] = useState(false)

  const [form, setForm] = useState<FormData>({
    username: '',
    title: '',
    bio: '',
    selectedSectors: [],
    languages: '',
    qualification: '',
    fieldOfStudy: '',
    experienceYears: '',
    previousWork: '',
    legalFirstName: '',
    legalLastName: '',
    dob: '',
    streetAddress: '',
    city: '',
    state: '',
    zip: '',
    country: '',
    idType: '',
    consentData: false,
    consentTerms: false,
  })

  const update = (key: keyof FormData, value: string | boolean) => {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  const toggleSector = (sector: string) => {
    setForm((prev) => ({
      ...prev,
      selectedSectors: prev.selectedSectors.includes(sector)
        ? prev.selectedSectors.filter((s) => s !== sector)
        : [...prev.selectedSectors, sector],
    }))
  }

  const handleContinue = () => {
    if (currentStep < STEPS.length - 1) setCurrentStep((s) => s + 1)
  }

  const handleBack = () => {
    if (currentStep > 0) setCurrentStep((s) => s - 1)
  }

  const handleSubmit = () => {
    setSuccessOpen(true)
  }

  const stepClass = (i: number) => {
    if (i < currentStep) return 'step-done'
    if (i === currentStep) return 'step-active'
    return 'step-inactive'
  }

  return (
    <div className="min-h-screen bg-ink-50">
      <Navbar />
      <div className="pt-16 max-w-2xl mx-auto px-4 py-10">
        <h1 className="font-heading font-medium text-3xl text-ink-900 mb-2 text-center">Become an Advisor</h1>
        <p className="text-ink-500 text-center mb-8">Join our verified network and start helping people today.</p>

        {/* Stepper */}
        <div className="flex items-center mb-8">
          {STEPS.map((label, i) => (
            <div key={label} className="flex items-center flex-1 last:flex-none">
              <div className="flex flex-col items-center gap-1">
                <div
                  className={`w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold transition-all ${stepClass(i)}`}
                >
                  {i < currentStep ? <CheckCircle className="w-5 h-5" /> : i + 1}
                </div>
                <span className={`text-xs font-medium hidden sm:block whitespace-nowrap ${
                  i === currentStep ? 'text-oxblood-700' : i < currentStep ? 'text-pine-600' : 'text-ink-400'
                }`}>
                  {label}
                </span>
              </div>
              {i < STEPS.length - 1 && (
                <div className={`flex-1 h-0.5 mx-2 mt-[-14px] sm:mt-[-28px] transition-all ${
                  i < currentStep ? 'bg-pine-600' : 'bg-ink-200'
                }`} />
              )}
            </div>
          ))}
        </div>

        {/* Card */}
        <div className="bg-white rounded-xl border border-ink-200 p-8">
          {/* STEP 0 — Public Profile */}
          {currentStep === 0 && (
            <div className="space-y-5">
              <h2 className="font-heading font-semibold text-xl text-ink-900">Public Profile</h2>
              <p className="text-sm text-ink-500">This information will be visible to users browsing advisors.</p>

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-1.5">Username</label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400 font-medium">@</span>
                  <input
                    type="text"
                    placeholder="YourHandle"
                    value={form.username}
                    onChange={(e) => update('username', e.target.value)}
                    className="input-base pl-8"
                  />
                </div>
              </div>

              <Input
                label="Professional Title"
                placeholder="e.g. Licensed Clinical Psychologist"
                value={form.title}
                onChange={(e) => update('title', e.target.value)}
              />

              <Textarea
                label="Bio"
                placeholder="Tell potential clients about your expertise, approach, and how you can help..."
                rows={4}
                value={form.bio}
                onChange={(e) => update('bio', e.target.value)}
              />

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">Sectors (select all that apply)</label>
                <div className="grid grid-cols-2 gap-2">
                  {SECTORS.map((sector) => (
                    <label
                      key={sector}
                      className={`flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
                        form.selectedSectors.includes(sector)
                          ? 'border-oxblood-700 bg-oxblood-50'
                          : 'border-ink-200 hover:border-ink-300'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={form.selectedSectors.includes(sector)}
                        onChange={() => toggleSector(sector)}
                        className="w-4 h-4 accent-oxblood-600 rounded"
                      />
                      <span className="text-sm font-medium text-ink-700">{sector}</span>
                    </label>
                  ))}
                </div>
              </div>

              <Input
                label="Languages"
                placeholder="e.g. English, Spanish"
                value={form.languages}
                onChange={(e) => update('languages', e.target.value)}
                helper="Separate multiple languages with commas"
              />
            </div>
          )}

          {/* STEP 1 — Credentials */}
          {currentStep === 1 && (
            <div className="space-y-5">
              <h2 className="font-heading font-semibold text-xl text-ink-900">Credentials</h2>
              <p className="text-sm text-ink-500">These will be verified by our team before your profile goes live.</p>

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-1.5">Highest Qualification</label>
                <select
                  value={form.qualification}
                  onChange={(e) => update('qualification', e.target.value)}
                  className="input-base"
                >
                  <option value="">Select qualification...</option>
                  <option>PhD</option>
                  <option>Master's Degree</option>
                  <option>Bachelor's Degree</option>
                  <option>Professional Certificate</option>
                  <option>Professional License</option>
                  <option>Other</option>
                </select>
              </div>

              <Input
                label="Field of Study / Specialisation"
                placeholder="e.g. Clinical Psychology"
                value={form.fieldOfStudy}
                onChange={(e) => update('fieldOfStudy', e.target.value)}
              />

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-1.5">Years of Experience</label>
                <select
                  value={form.experienceYears}
                  onChange={(e) => update('experienceYears', e.target.value)}
                  className="input-base"
                >
                  <option value="">Select range...</option>
                  <option>1–2 years</option>
                  <option>3–5 years</option>
                  <option>6–10 years</option>
                  <option>11–15 years</option>
                  <option>15+ years</option>
                </select>
              </div>

              {/* Degree upload */}
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-1.5">
                  Upload Degree / Certificate <span className="text-danger-600">*</span>
                </label>
                <div className="border-2 border-dashed border-ink-200 rounded-xl p-8 text-center hover:border-oxblood-700 hover:bg-oxblood-50 transition-all cursor-pointer">
                  <Upload className="w-8 h-8 text-ink-400 mx-auto mb-2" />
                  <p className="text-sm font-medium text-ink-700 mb-1">Click to upload or drag & drop</p>
                  <p className="text-xs text-ink-400">PDF, JPG, PNG up to 10MB</p>
                </div>
              </div>

              {/* License upload (optional) */}
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-1.5">
                  Upload License / Registration <span className="text-ink-400 font-normal">(optional)</span>
                </label>
                <div className="border-2 border-dashed border-ink-100 rounded-xl p-5 text-center hover:border-ink-300 transition-all cursor-pointer">
                  <Upload className="w-6 h-6 text-ink-300 mx-auto mb-1" />
                  <p className="text-xs text-ink-400">PDF, JPG, PNG up to 10MB</p>
                </div>
              </div>

              <Textarea
                label="Previous Work / Experience Summary"
                placeholder="Briefly describe your professional experience, notable achievements, or previous roles..."
                rows={4}
                value={form.previousWork}
                onChange={(e) => update('previousWork', e.target.value)}
              />
            </div>
          )}

          {/* STEP 2 — Identity Verification */}
          {currentStep === 2 && (
            <div className="space-y-5">
              <h2 className="font-heading font-semibold text-xl text-ink-900">Identity Verification</h2>

              {/* Warning banner */}
              <div className="bg-warn-100 border border-warn-500/30 rounded-xl p-4 flex gap-3">
                <AlertTriangle className="w-5 h-5 text-warn-500 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-semibold text-ink-800">Strictly Private — Never Shown to Users</p>
                  <p className="text-xs text-ink-600 mt-0.5">
                    This information is encrypted and only accessed by AdvisorConnect's compliance team during the verification process. It will never be shared with users or visible on your public profile.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <Input
                  label="Legal First Name"
                  placeholder="John"
                  value={form.legalFirstName}
                  onChange={(e) => update('legalFirstName', e.target.value)}
                />
                <Input
                  label="Legal Last Name"
                  placeholder="Smith"
                  value={form.legalLastName}
                  onChange={(e) => update('legalLastName', e.target.value)}
                />
              </div>

              <Input
                label="Date of Birth"
                type="date"
                value={form.dob}
                onChange={(e) => update('dob', e.target.value)}
              />

              <Input
                label="Street Address"
                placeholder="123 Main Street, Apt 4B"
                value={form.streetAddress}
                onChange={(e) => update('streetAddress', e.target.value)}
              />

              <div className="grid grid-cols-3 gap-3">
                <Input
                  label="City"
                  placeholder="New York"
                  value={form.city}
                  onChange={(e) => update('city', e.target.value)}
                />
                <Input
                  label="State / Province"
                  placeholder="NY"
                  value={form.state}
                  onChange={(e) => update('state', e.target.value)}
                />
                <Input
                  label="ZIP / Postal Code"
                  placeholder="10001"
                  value={form.zip}
                  onChange={(e) => update('zip', e.target.value)}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-1.5">Country</label>
                <select
                  value={form.country}
                  onChange={(e) => update('country', e.target.value)}
                  className="input-base"
                >
                  <option value="">Select country...</option>
                  <option>United States</option>
                  <option>United Kingdom</option>
                  <option>Canada</option>
                  <option>Australia</option>
                  <option>India</option>
                  <option>Germany</option>
                  <option>France</option>
                  <option>Other</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-1.5">Government ID Type</label>
                <select
                  value={form.idType}
                  onChange={(e) => update('idType', e.target.value)}
                  className="input-base"
                >
                  <option value="">Select ID type...</option>
                  <option>Passport</option>
                  <option>Driver's License</option>
                  <option>National ID Card</option>
                </select>
              </div>

              {/* ID upload — front and back */}
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">Upload Government ID</label>
                <div className="grid grid-cols-2 gap-3">
                  <div className="border-2 border-dashed border-ink-200 rounded-xl p-5 text-center hover:border-oxblood-700 hover:bg-oxblood-50 transition-all cursor-pointer">
                    <Upload className="w-6 h-6 text-ink-400 mx-auto mb-1.5" />
                    <p className="text-xs font-medium text-ink-600">Front Side</p>
                    <p className="text-xs text-ink-400 mt-0.5">JPG, PNG up to 5MB</p>
                  </div>
                  <div className="border-2 border-dashed border-ink-200 rounded-xl p-5 text-center hover:border-oxblood-700 hover:bg-oxblood-50 transition-all cursor-pointer">
                    <Upload className="w-6 h-6 text-ink-400 mx-auto mb-1.5" />
                    <p className="text-xs font-medium text-ink-600">Back Side</p>
                    <p className="text-xs text-ink-400 mt-0.5">JPG, PNG up to 5MB</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* STEP 3 — Review & Submit */}
          {currentStep === 3 && (
            <div className="space-y-5">
              <h2 className="font-heading font-semibold text-xl text-ink-900">Review & Submit</h2>
              <p className="text-sm text-ink-500">Please review your application before submitting. Our team will verify within 2–3 business days.</p>

              {/* Public Profile summary */}
              <div className="border border-ink-200 rounded-xl p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-semibold text-ink-800">Public Profile</h3>
                  <button onClick={() => setCurrentStep(0)} className="text-xs text-oxblood-700 font-medium hover:text-oxblood-600">Edit</button>
                </div>
                <div className="space-y-1.5 text-sm text-ink-600">
                  <p><span className="text-ink-400">Username:</span> @{form.username || 'Not set'}</p>
                  <p><span className="text-ink-400">Title:</span> {form.title || 'Not set'}</p>
                  <p><span className="text-ink-400">Sectors:</span> {form.selectedSectors.join(', ') || 'None selected'}</p>
                  <p><span className="text-ink-400">Languages:</span> {form.languages || 'Not set'}</p>
                </div>
              </div>

              {/* Credentials summary */}
              <div className="border border-ink-200 rounded-xl p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-semibold text-ink-800">Credentials</h3>
                  <button onClick={() => setCurrentStep(1)} className="text-xs text-oxblood-700 font-medium hover:text-oxblood-600">Edit</button>
                </div>
                <div className="space-y-1.5 text-sm text-ink-600">
                  <p><span className="text-ink-400">Qualification:</span> {form.qualification || 'Not set'}</p>
                  <p><span className="text-ink-400">Field of Study:</span> {form.fieldOfStudy || 'Not set'}</p>
                  <p><span className="text-ink-400">Experience:</span> {form.experienceYears || 'Not set'}</p>
                </div>
              </div>

              {/* Identity summary (private) */}
              <div className="border border-danger-600/30 rounded-xl p-4 bg-danger-100/30">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-semibold text-ink-800">Identity (Private)</h3>
                    <span className="bg-danger-100 text-danger-600 text-xs font-bold px-2 py-0.5 rounded-full">Admin Eyes Only</span>
                  </div>
                  <button onClick={() => setCurrentStep(2)} className="text-xs text-oxblood-700 font-medium hover:text-oxblood-600">Edit</button>
                </div>
                <div className="space-y-1.5 text-sm text-ink-600">
                  <p><span className="text-ink-400">Legal Name:</span> <span className="blur-sm select-none">{form.legalFirstName || 'John'} {form.legalLastName || 'Smith'}</span></p>
                  <p><span className="text-ink-400">Date of Birth:</span> <span className="blur-sm select-none">{form.dob || '01/01/1990'}</span></p>
                  <p><span className="text-ink-400">Address:</span> <span className="blur-sm select-none">{form.streetAddress || '123 Main St'}</span></p>
                  <p><span className="text-ink-400">ID Type:</span> {form.idType || 'Not set'}</p>
                </div>
              </div>

              {/* Consents */}
              <div className="space-y-3 pt-2">
                <label className="flex items-start gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.consentData}
                    onChange={(e) => update('consentData', e.target.checked)}
                    className="w-4 h-4 mt-0.5 accent-oxblood-600"
                  />
                  <span className="text-sm text-ink-600">
                    I consent to AdvisorConnect storing and processing my personal data for identity verification purposes only.
                  </span>
                </label>
                <label className="flex items-start gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.consentTerms}
                    onChange={(e) => update('consentTerms', e.target.checked)}
                    className="w-4 h-4 mt-0.5 accent-oxblood-600"
                  />
                  <span className="text-sm text-ink-600">
                    I agree to the <a href="/advisor-terms" className="text-oxblood-700 underline">Advisor Terms of Service</a> and <a href="/privacy" className="text-oxblood-700 underline">Privacy Policy</a>.
                  </span>
                </label>
              </div>
            </div>
          )}

          {/* Navigation buttons */}
          <div className="flex justify-between mt-8 pt-6 border-t border-ink-100">
            {currentStep > 0 ? (
              <Button variant="outline" onClick={handleBack}>Back</Button>
            ) : (
              <div />
            )}
            {currentStep < STEPS.length - 1 ? (
              <Button variant="primary" onClick={handleContinue}>
                Continue →
              </Button>
            ) : (
              <Button
                variant="success"
                onClick={handleSubmit}
                disabled={!form.consentData || !form.consentTerms}
              >
                Submit Application
              </Button>
            )}
          </div>
        </div>
      </div>

      {/* Success modal */}
      <Modal open={successOpen} onClose={() => { setSuccessOpen(false); navigate('/') }}>
        <div className="text-center">
          <div className="w-16 h-16 bg-pine-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <CheckCircle className="w-8 h-8 text-pine-600" />
          </div>
          <h2 className="font-heading font-medium text-2xl text-ink-900 mb-2">Application Submitted!</h2>
          <p className="text-ink-500 mb-6 leading-relaxed">
            Thank you for applying to become an AdvisorConnect advisor. Our compliance team will review your application within <span className="font-semibold text-ink-900">2–3 business days</span>.
          </p>
          <p className="text-sm text-ink-400 mb-6">
            You'll receive an email notification once your application has been reviewed. Keep an eye on your inbox!
          </p>
          <Button variant="primary" fullWidth onClick={() => { setSuccessOpen(false); navigate('/') }}>
            Back to Home
          </Button>
        </div>
      </Modal>
    </div>
  )
}
