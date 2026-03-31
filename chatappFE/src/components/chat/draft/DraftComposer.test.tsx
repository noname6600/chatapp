/* @vitest-environment jsdom */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { DraftComposer } from './DraftComposer'
import { useDraftStore } from '../../../store/draft.store'

describe('DraftComposer Integration', () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    // Reset store before each test
    useDraftStore.setState({ drafts: {}, currentRoomId: null })
    useDraftStore.getState().setCurrentRoom('room-1')
  })

  it('should render empty state', () => {
    render(<DraftComposer roomId="room-1" />)

    expect(screen.getByText(/No content yet/i)).toBeTruthy()
  })

  it('should render blocks in order', () => {
    const { addTextBlock } = useDraftStore.getState()
    addTextBlock('room-1', 'First')
    addTextBlock('room-1', 'Second')
    addTextBlock('room-1', 'Third')

    render(<DraftComposer roomId="room-1" />)

    expect(screen.getByText('First')).toBeTruthy()
    expect(screen.getByText('Second')).toBeTruthy()
    expect(screen.getByText('Third')).toBeTruthy()

    // Verify order
    const texts = screen.getAllByText(/First|Second|Third/)
    expect(texts[0].textContent).toBe('First')
    expect(texts[1].textContent).toBe('Second')
    expect(texts[2].textContent).toBe('Third')
  })

  it('should add text block when + Text button clicked', async () => {
    const { addTextBlock } = useDraftStore.getState()
    addTextBlock('room-1', 'Existing')

    render(<DraftComposer roomId="room-1" />)

    const addTextBtn = screen.getByRole('button', { name: /\+ Text/ })
    fireEvent.click(addTextBtn)

    const draft = useDraftStore.getState().getDraft('room-1')
    expect(draft?.blocks).toHaveLength(2)
  })

  it('should handle media input', async () => {
    const { addTextBlock } = useDraftStore.getState()
    addTextBlock('room-1', 'Existing')

    render(<DraftComposer roomId="room-1" onAddMedia={vi.fn()} />)

    const input = screen.getByText(/\+ Media/)
      .closest('label')
      ?.querySelector('input[type="file"]') as HTMLInputElement

    expect(input).toBeTruthy()
    expect(input.accept).toContain('image')
  })

  it('should render mixed text and media blocks', () => {
    const { addTextBlock, addMediaBlock } = useDraftStore.getState()
    addTextBlock('room-1', 'Text 1')
    addMediaBlock('room-1', new File(['img'], 'test.png', { type: 'image/png' }))
    addTextBlock('room-1', 'Text 2')

    render(<DraftComposer roomId="room-1" />)

    expect(screen.getByText('Text 1')).toBeTruthy()
    expect(screen.getByText('Text 2')).toBeTruthy()
  })

  it('should handle text block editing', async () => {
    const { addTextBlock } = useDraftStore.getState()
    addTextBlock('room-1', 'Original')

    const { rerender } = render(<DraftComposer roomId="room-1" />)

    // Click to edit
    const blockText = screen.getByText('Original')
    fireEvent.click(blockText)

    // Re-render to show edit state
    rerender(<DraftComposer roomId="room-1" />)

    await waitFor(() => {
      const textarea = screen.getByDisplayValue('Original')
      expect(textarea).toBeTruthy()
    })
  })
})
