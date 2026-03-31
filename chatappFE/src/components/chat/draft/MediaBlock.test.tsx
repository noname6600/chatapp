import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MediaBlockComponent } from './MediaBlock'
import { useDraftStore } from '../../../store/draft.store'
import type { MediaBlock } from '../../../types/draft'

describe('MediaBlock Component', () => {
  beforeEach(() => {
    // Reset store before each test
    useDraftStore.setState({ drafts: {}, currentRoomId: null })
  })

  it('should render image block with preview', async () => {
    const imageFile = new File(['image content'], 'test.png', {
      type: 'image/png',
    })
    const block: MediaBlock = {
      id: 'block-1',
      type: 'media',
      file: imageFile,
    }

    useDraftStore.setState({
      drafts: {
        'room-1': {
          roomId: 'room-1',
          blocks: [block],
        },
      },
    })

    render(<MediaBlockComponent block={block} roomId="room-1" />)

    // Should render img element
    await waitFor(() => {
      const img = screen.getByRole('img')
      expect(img).toBeInTheDocument()
      expect(img).toHaveAttribute('alt', 'test.png')
    })
  })

  it('should render file block with icon', () => {
    const file = new File(['file content'], 'document.pdf', {
      type: 'application/pdf',
    })
    const block: MediaBlock = {
      id: 'block-1',
      type: 'media',
      file,
    }

    useDraftStore.setState({
      drafts: {
        'room-1': {
          roomId: 'room-1',
          blocks: [block],
        },
      },
    })

    render(<MediaBlockComponent block={block} roomId="room-1" />)

    expect(screen.getByText('document.pdf')).toBeInTheDocument()
    expect(screen.getByText(/📎/)).toBeInTheDocument()
  })

  it('should show delete button on hover', async () => {
    const file = new File(['content'], 'test.txt', { type: 'text/plain' })
    const block: MediaBlock = {
      id: 'block-1',
      type: 'media',
      file,
    }

    useDraftStore.setState({
      drafts: {
        'room-1': {
          roomId: 'room-1',
          blocks: [block],
        },
      },
    })

    render(<MediaBlockComponent block={block} roomId="room-1" />)

    const container = screen.getByText('test.txt').closest('.group')!

    // Hover over the container
    fireEvent.mouseEnter(container)

    await waitFor(() => {
      const deleteBtn = screen.getByRole('button', { name: /Remove media/i })
      expect(deleteBtn).toBeVisible()
    })
  })

  it('should remove block when delete button clicked', async () => {
    const file = new File(['content'], 'test.txt', { type: 'text/plain' })
    const block: MediaBlock = {
      id: 'block-1',
      type: 'media',
      file,
    }

    useDraftStore.setState({
      drafts: {
        'room-1': {
          roomId: 'room-1',
          blocks: [block],
        },
      },
    })

    render(<MediaBlockComponent block={block} roomId="room-1" />)

    const container = screen.getByText('test.txt').closest('.group')!
    fireEvent.mouseEnter(container)

    await waitFor(() => {
      const deleteBtn = screen.getByRole('button', { name: /Remove media/i })
      fireEvent.click(deleteBtn)
    })

    // Verify store was updated
    const draft = useDraftStore.getState().getDraft('room-1')
    expect(draft?.blocks).toHaveLength(0)
  })

  it('should display file size', () => {
    const file = new File(['content'], 'test.txt', { type: 'text/plain' })
    Object.defineProperty(file, 'size', { value: 1024 * 2 }) // 2KB

    const block: MediaBlock = {
      id: 'block-1',
      type: 'media',
      file,
    }

    useDraftStore.setState({
      drafts: {
        'room-1': {
          roomId: 'room-1',
          blocks: [block],
        },
      },
    })

    render(<MediaBlockComponent block={block} roomId="room-1" />)

    expect(screen.getByText(/2\.0 KB/)).toBeInTheDocument()
  })
})
