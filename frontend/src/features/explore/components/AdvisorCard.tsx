import { useNavigate } from 'react-router-dom'
import { Star, CheckCircle } from 'lucide-react'
import { Card } from '@/shared/components/ui/Card'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Badge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import type { AdvisorPublic } from '@/types'

interface AdvisorCardProps {
  advisor: AdvisorPublic
  onChatClick?: () => void
  onBookClick?: () => void
}

export function AdvisorCard({ advisor, onChatClick, onBookClick }: AdvisorCardProps) {
  const navigate = useNavigate()

  const handleCardClick = () => {
    navigate(`/advisor/${advisor.username}`)
  }

  const handleChatClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    onChatClick?.()
    navigate('/chat')
  }

  const handleBookClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    onBookClick?.()
    navigate(`/book/${advisor.id}`)
  }

  return (
    <Card hover onClick={handleCardClick} className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-start gap-3 mb-3">
        <Avatar username={advisor.username} color={advisor.color} size="lg" showOnline={advisor.isOnline} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="font-heading font-medium text-ink-900 text-sm truncate">
              {advisor.username}
            </span>
            {advisor.isVerified && (
              <CheckCircle className="w-4 h-4 text-pine-600 flex-shrink-0" aria-label="Verified advisor" />
            )}
          </div>
          <p className="text-xs text-ink-500 mt-0.5 leading-tight">{advisor.title}</p>
          {/* Rating row */}
          <div className="flex items-center gap-2 mt-1">
            <div className="flex items-center gap-0.5">
              <Star className="w-3.5 h-3.5 text-warn-500 fill-warn-500" />
              <span className="text-xs font-semibold text-ink-700">{advisor.rating.toFixed(1)}</span>
            </div>
            <span className="text-xs text-ink-400">({advisor.reviewCount} reviews)</span>
            {advisor.isOnline && (
              <span className="flex items-center gap-1 text-xs text-pine-600 font-medium ml-auto">
                <span className="w-1.5 h-1.5 rounded-full bg-pine-600 inline-block" />
                Online
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Bio */}
      <p className="text-xs text-ink-600 leading-relaxed mb-3 line-clamp-2 flex-1">
        {advisor.bio}
      </p>

      {/* Tags */}
      <div className="flex flex-wrap gap-1.5 mb-4">
        {advisor.tags.slice(0, 3).map((tag) => (
          <Badge key={tag} variant="brand">{tag}</Badge>
        ))}
      </div>

      {/* Action buttons */}
      <div className="flex gap-2 mt-auto">
        <Button
          variant="outline"
          size="sm"
          className="flex-1 text-xs"
          onClick={handleChatClick}
        >
          Free Chat
        </Button>
        <Button
          variant="primary"
          size="sm"
          className="flex-1 text-xs"
          onClick={handleBookClick}
        >
          Book Video $39
        </Button>
      </div>
    </Card>
  )
}
