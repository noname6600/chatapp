let ctx: AudioContext | null = null
let ringing = false
let nextTimer: ReturnType<typeof setTimeout> | null = null

function scheduleRing(audioCtx: AudioContext, at: number) {
  for (let i = 0; i < 2; i++) {
    const start = at + i * 1.0
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()
    osc.connect(gain)
    gain.connect(audioCtx.destination)
    osc.type = "sine"
    osc.frequency.value = 800
    gain.gain.setValueAtTime(0, start)
    gain.gain.linearRampToValueAtTime(0.25, start + 0.02)
    gain.gain.setValueAtTime(0.25, start + 0.78)
    gain.gain.linearRampToValueAtTime(0, start + 0.8)
    osc.start(start)
    osc.stop(start + 0.8)
  }
}

function loop(audioCtx: AudioContext) {
  if (!ringing) return
  const at = audioCtx.currentTime
  scheduleRing(audioCtx, at)
  // Next ring cycle in 3s (2×0.8s beep + 1.4s silence)
  nextTimer = setTimeout(() => loop(audioCtx), 3000)
}

export function startRingtone() {
  if (ringing) return
  ringing = true
  try {
    ctx = new AudioContext()
    loop(ctx)
  } catch {
    ringing = false
  }
}

export function stopRingtone() {
  ringing = false
  if (nextTimer !== null) { clearTimeout(nextTimer); nextTimer = null }
  if (ctx) { ctx.close().catch(() => {}); ctx = null }
}
