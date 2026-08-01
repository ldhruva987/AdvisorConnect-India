import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, Send, Video, User } from 'lucide-react'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Button } from '@/shared/components/ui/Button'
import { Navbar } from '@/shared/components/layout/Navbar'
import type { Conversation, Message } from '@/types'

const MOCK_CONVERSATIONS: Conversation[] = [
  {
    id: 'conv-1',
    advisorId: '1',
    advisorUsername: '@MindfulRohan',
    advisorColor: '#8a3f24',
    lastMessage: 'That sounds really challenging. Have you tried the breathing exercise I mentioned?',
    lastMessageAt: '2m ago',
    unreadCount: 2,
    isAdvisorOnline: true,
  },
  {
    id: 'conv-2',
    advisorId: '2',
    advisorUsername: '@SarahCareerPro',
    advisorColor: '#784f00',
    lastMessage: 'Your resume looks great! Let\'s work on your LinkedIn profile next.',
    lastMessageAt: '1h ago',
    unreadCount: 0,
    isAdvisorOnline: true,
  },
  {
    id: 'conv-3',
    advisorId: '3',
    advisorUsername: '@FinanceWithTed',
    advisorColor: '#516000',
    lastMessage: 'I\'d recommend diversifying your portfolio before thinking about real estate.',
    lastMessageAt: 'Yesterday',
    unreadCount: 0,
    isAdvisorOnline: false,
  },
]

const INITIAL_MESSAGES: Message[] = [
  {
    id: 'msg-1',
    conversationId: 'conv-1',
    senderId: 'advisor-1',
    senderType: 'advisor',
    text: 'Hi! I\'m Rohan. Thanks for reaching out. How can I help you today?',
    createdAt: '10:02 AM',
  },
  {
    id: 'msg-2',
    conversationId: 'conv-1',
    senderId: 'user-1',
    senderType: 'user',
    text: 'Hi Rohan! I\'ve been feeling really overwhelmed lately with work and I think it\'s affecting my sleep.',
    createdAt: '10:04 AM',
  },
  {
    id: 'msg-3',
    conversationId: 'conv-1',
    senderId: 'advisor-1',
    senderType: 'advisor',
    text: 'I hear you — that combination of work stress and disrupted sleep is really tough. It\'s actually very common for work pressure to create a cycle where stress affects sleep, and poor sleep makes the stress worse. Can you tell me more about what\'s been happening at work?',
    createdAt: '10:06 AM',
  },
  {
    id: 'msg-4',
    conversationId: 'conv-1',
    senderId: 'user-1',
    senderType: 'user',
    text: 'It\'s been a big project deadline coming up. I keep thinking about it even at night. My mind just won\'t switch off.',
    createdAt: '10:08 AM',
  },
  {
    id: 'msg-5',
    conversationId: 'conv-1',
    senderId: 'advisor-1',
    senderType: 'advisor',
    text: 'That sounds really challenging. What you\'re describing is called "rumination" — when your brain keeps replaying worries. The good news is there are some very effective techniques to create a mental boundary between work and rest. Would you like me to walk you through a quick CBT-based wind-down exercise?',
    createdAt: '10:10 AM',
  },
]

const ADVISOR_REPLIES = [
  'That\'s a really insightful question. Let me share some techniques that have worked well for my clients...',
  'I understand where you\'re coming from. This is something many people struggle with, and it\'s completely normal.',
  'Based on what you\'ve shared, I think a CBT approach could be really helpful here. Would you be open to trying a small exercise?',
  'Great question! The research actually shows that even 10 minutes of mindfulness per day can make a significant difference.',
  'I\'m glad you reached out. Let\'s take this step by step and figure out the best path forward for you.',
]

export function ChatPage() {
  const navigate = useNavigate()
  const [activeConvId, setActiveConvId] = useState('conv-1')
  const [messages, setMessages] = useState<Message[]>(INITIAL_MESSAGES)
  const [newMessage, setNewMessage] = useState('')
  const [advisorTyping, setAdvisorTyping] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const replyIndex = useRef(0)

  const activeConv = MOCK_CONVERSATIONS.find((c) => c.id === activeConvId)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, advisorTyping])

  const handleSend = () => {
    const text = newMessage.trim()
    if (!text) return

    const userMsg: Message = {
      id: `msg-${Date.now()}`,
      conversationId: activeConvId,
      senderId: 'user-1',
      senderType: 'user',
      text,
      createdAt: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    }

    setMessages((prev) => [...prev, userMsg])
    setNewMessage('')
    setAdvisorTyping(true)

    setTimeout(() => {
      setAdvisorTyping(false)
      const advisorMsg: Message = {
        id: `msg-${Date.now() + 1}`,
        conversationId: activeConvId,
        senderId: 'advisor-1',
        senderType: 'advisor',
        text: ADVISOR_REPLIES[replyIndex.current % ADVISOR_REPLIES.length],
        createdAt: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      }
      replyIndex.current += 1
      setMessages((prev) => [...prev, advisorMsg])
    }, 1500)
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const filteredConvs = MOCK_CONVERSATIONS.filter((c) =>
    c.advisorUsername.toLowerCase().includes(searchQuery.toLowerCase())
  )

  return (
    <div className="fixed top-0 w-full pt-16 flex h-screen bg-ink-50">
      <Navbar />

      {/* Sidebar */}
      <aside className="w-72 bg-white border-r border-ink-200 hidden md:flex flex-col flex-shrink-0">
        <div className="p-4 border-b border-ink-100">
          <h2 className="font-heading font-semibold text-ink-900 mb-3">My Conversations</h2>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-400" />
            <input
              type="text"
              placeholder="Search..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-sm border border-ink-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-oxblood-700 bg-ink-50"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {filteredConvs.map((conv) => (
            <button
              key={conv.id}
              onClick={() => setActiveConvId(conv.id)}
              className={`w-full flex items-start gap-3 p-4 hover:bg-ink-50 transition-colors text-left ${
                activeConvId === conv.id ? 'bg-oxblood-50' : ''
              }`}
            >
              <Avatar
                username={conv.advisorUsername}
                color={conv.advisorColor}
                size="md"
                showOnline={conv.isAdvisorOnline}
              />
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-0.5">
                  <span className="text-sm font-semibold text-ink-900 truncate">{conv.advisorUsername}</span>
                  <span className="text-xs text-ink-400 flex-shrink-0 ml-1">{conv.lastMessageAt}</span>
                </div>
                <p className="text-xs text-ink-500 truncate">{conv.lastMessage}</p>
              </div>
              {conv.unreadCount > 0 && (
                <span className="w-5 h-5 rounded-full bg-oxblood-700 text-white text-xs font-bold flex items-center justify-center flex-shrink-0 mt-0.5">
                  {conv.unreadCount}
                </span>
              )}
            </button>
          ))}
        </div>
      </aside>

      {/* Main pane */}
      <main className="flex-1 flex flex-col bg-ink-50 h-[calc(100vh-64px)]">
        {activeConv ? (
          <>
            {/* Chat header */}
            <div className="bg-white border-b border-ink-200 px-5 py-3 flex items-center gap-3">
              <Avatar
                username={activeConv.advisorUsername}
                color={activeConv.advisorColor}
                size="md"
                showOnline={activeConv.isAdvisorOnline}
              />
              <div className="flex-1">
                <h3 className="font-semibold text-ink-900 text-sm">{activeConv.advisorUsername}</h3>
                <p className="text-xs text-ink-400">
                  {activeConv.isAdvisorOnline ? '🟢 Online · Responds in <5 min' : '⚫ Offline'}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => navigate(`/advisor/${activeConv.advisorUsername}`)}
                >
                  <User className="w-4 h-4" />
                  View Profile
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => navigate(`/book/${activeConv.advisorId}`)}
                >
                  <Video className="w-4 h-4" />
                  Book Video
                </Button>
              </div>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              {/* Free chat pill */}
              <div className="flex justify-center">
                <span className="bg-pine-100 text-pine-600 text-xs font-semibold px-4 py-1.5 rounded-full">
                  ✓ Free chat — unlimited messages
                </span>
              </div>

              {messages
                .filter((m) => m.conversationId === activeConvId)
                .map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex ${msg.senderType === 'user' ? 'justify-end' : 'justify-start items-end gap-2'}`}
                  >
                    {msg.senderType === 'advisor' && (
                      <Avatar
                        username={activeConv.advisorUsername}
                        color={activeConv.advisorColor}
                        size="sm"
                      />
                    )}
                    <div className="max-w-[70%]">
                      <div
                        className={`px-4 py-3 text-sm leading-relaxed ${
                          msg.senderType === 'user' ? 'msg-bubble-user' : 'msg-bubble-advisor'
                        }`}
                      >
                        {msg.text}
                      </div>
                      <p className={`text-xs text-ink-400 mt-1 ${msg.senderType === 'user' ? 'text-right' : ''}`}>
                        {msg.createdAt}
                      </p>
                    </div>
                  </div>
                ))}

              {/* Typing indicator */}
              {advisorTyping && (
                <div className="flex items-end gap-2">
                  <Avatar
                    username={activeConv.advisorUsername}
                    color={activeConv.advisorColor}
                    size="sm"
                  />
                  <div className="msg-bubble-advisor px-4 py-3">
                    <div className="flex gap-1 items-center h-4">
                      {[0, 1, 2].map((i) => (
                        <span
                          key={i}
                          className="w-2 h-2 rounded-full bg-ink-400 animate-bounce"
                          style={{ animationDelay: `${i * 0.15}s` }}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* Input bar */}
            <div className="bg-white border-t border-ink-200 p-4">
              <div className="flex items-end gap-3">
                <textarea
                  value={newMessage}
                  onChange={(e) => setNewMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="Type a message..."
                  rows={1}
                  className="flex-1 input-base resize-none max-h-32 py-2.5 text-sm"
                  style={{ minHeight: '44px' }}
                />
                <button
                  onClick={handleSend}
                  disabled={!newMessage.trim()}
                  className="w-11 h-11 bg-oxblood-600 hover:bg-oxblood-700 disabled:opacity-40 rounded-lg flex items-center justify-center transition-colors flex-shrink-0"
                  aria-label="Send message"
                >
                  <Send className="w-4 h-4 text-white" />
                </button>
              </div>
              <p className="text-xs text-ink-400 mt-2 text-center">
                Chat is always free. Book a video session for deeper consultation.
              </p>
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-ink-400">
            <p>Select a conversation to start chatting</p>
          </div>
        )}
      </main>
    </div>
  )
}
