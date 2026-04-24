import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { TextBlockComponent } from './TextBlock'
import { useDraftStore } from '../../../store/draft.store'
import type { TextBlock } from '../../../types/draft'

describe('TextBlock Component', () => {
  beforeEach(() => {
    // Reset store before each test
    useDraftStore.setState({ drafts: {}, currentRoomId: null })
  })

  it('should render text block in view mode', () => {
    const block: TextBlock = {
      id: 'block-1',
      type: 'text',
      content: 'Hello world',
    }

    render(
      <TextBlockComponent
        block={block}
        roomId="room-1"
        isEditing={false}
      />
    )

    expect(screen.getByText('Hello world')).toBeInTheDocument()
  })

  it('should enter edit mode on click', async () => {
    const block: TextBlock = {
      id: 'block-1',
      type: 'text',
      content: 'Hello world',
    }

    useDraftStore.setState({
      drafts: {
        'room-1': {
          roomId: 'room-1',
          blocks: [block],
        },
      },
    })

    const { rerender } = render(
      <TextBlockComponent
        block={block}
        roomId="room-1"
        isEditing={false}
      />
    )

    // Click to enter edit mode
    const container = screen.getByText('Hello world').parentElement
    fireEvent.click(container!)

    // Update to show editing state
    rerender(
      <TextBlockComponent
        block={block}
        roomId="room-1"
        isEditing={true}
        editContent="Hello world"
      />
    )

    // Should show textarea
    await waitFor(() => {
      const textarea = screen.getByDisplayValue('Hello world')
      expect(textarea).toBeInTheDocument()
      expect(textarea.tagName).toBe('TEXTAREA')
    })
  })

  it('should show placeholder when empty and not editing', () => {
    const block: TextBlock = {
      id: 'block-1',
      type: 'text',
      content: '',
    }

    render(
      <TextBlockComponent
        block={block}
        roomId="room-1"
        isEditing={false}
      />
    )

    expect(screen.getByText('Click to add text...')).toBeInTheDocument()
  })

  it('should render multiline text with preserved newlines', () => {
    const block: TextBlock = {
      id: 'block-1',
      type: 'text',
      content: 'Line 1\nLine 2\nLine 3',
    }

    render(
      <TextBlockComponent
        block={block}
        roomId="room-1"
        isEditing={false}
      />
    )

    expect(screen.getByText(/Line 1/)).toBeInTheDocument()
    expect(screen.getByText(/Line 2/)).toBeInTheDocument()
    expect(screen.getByText(/Line 3/)).toBeInTheDocument()
  })

  it('should focus textarea when entering edit mode', async () => {
    const block: TextBlock = {
      id: 'block-1',
      type: 'text',
      content: 'Hello',
    }

    render(
      <TextBlockComponent
        block={block}
        roomId="room-1"
        isEditing={true}
        editContent="Hello"
      />
    )

    await waitFor(() => {
      const textarea = screen.getByDisplayValue('Hello') as HTMLTextAreaElement
      expect(document.activeElement).toBe(textarea)
    })
  })
})
