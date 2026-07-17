let ctx: AudioContext | null = null

function getCtx(): AudioContext | null {
  if (typeof window === "undefined") return null
  if (!ctx) {
    try {
      ctx = new AudioContext()
    } catch {
      return null
    }
  }
  return ctx
}

function beep(
  audioCtx: AudioContext,
  freq: number,
  startAt: number,
  duration: number,
  volume = 0.22
) {
  const osc = audioCtx.createOscillator()
  const gain = audioCtx.createGain()
  osc.connect(gain)
  gain.connect(audioCtx.destination)
  osc.type = "sine"
  osc.frequency.setValueAtTime(freq, startAt)
  gain.gain.setValueAtTime(volume, startAt)
  gain.gain.exponentialRampToValueAtTime(0.001, startAt + duration)
  osc.start(startAt)
  osc.stop(startAt + duration)
}

// Resume context if suspended (Chrome suspends AudioContext in background tabs),
// then invoke the play callback once running.
function withRunningCtx(fn: (audioCtx: AudioContext) => void) {
  const audioCtx = getCtx()
  if (!audioCtx) return

  if (audioCtx.state === "running") {
    fn(audioCtx)
    return
  }

  // suspended → resume first, then play
  void audioCtx.resume().then(() => {
    if (audioCtx.state === "running") fn(audioCtx)
  })
}

export function playMessageSound() {
  withRunningCtx((audioCtx) => {
    beep(audioCtx, 660, audioCtx.currentTime, 0.14)
  })
}

export function playMentionSound() {
  withRunningCtx((audioCtx) => {
    const t = audioCtx.currentTime
    beep(audioCtx, 880, t, 0.1, 0.3)
    beep(audioCtx, 1100, t + 0.13, 0.12, 0.3)
  })
}

// Prime the AudioContext on first user interaction so resume() calls later always succeed.
if (typeof window !== "undefined") {
  const prime = () => { void getCtx()?.resume() }
  window.addEventListener("click", prime, { once: true })
  window.addEventListener("keydown", prime, { once: true })
}
