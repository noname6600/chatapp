import { useEffect, useMemo, useState } from "react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"

import { exchangeGoogleOAuthCodeApi } from "../api/auth.service"
import { useAuth } from "../hooks/useAuth"

const toOAuthErrorMessage = (errorCode: string | null) => {
  if (errorCode === "incomplete_account") {
    return "Account setup incomplete. Please try again in a few seconds."
  }
  if (errorCode === "google_login_failed") {
    return "Google login failed. Please try again."
  }
  return "Google login could not be completed. Please try again."
}

export default function GoogleOAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { accessToken, login } = useAuth()
  const [error, setError] = useState("")
  const [attemptKey, setAttemptKey] = useState(0)
  const [isExchanging, setIsExchanging] = useState(false)

  const oauthError = useMemo(
    () => searchParams.get("oauth_error") || searchParams.get("error"),
    [searchParams]
  )
  const code = useMemo(() => searchParams.get("code"), [searchParams])

  useEffect(() => {
    if (accessToken) {
      navigate("/chat", { replace: true })
    }
  }, [accessToken, navigate])

  useEffect(() => {
    let active = true

    if (oauthError) {
      setError(toOAuthErrorMessage(oauthError))
      return () => {
        active = false
      }
    }

    if (!code) {
      setError(toOAuthErrorMessage(null))
      return () => {
        active = false
      }
    }

    const processedKey = `oauth_google_processed:${code}`
    if (sessionStorage.getItem(processedKey) === "1") {
      setError("OAuth code was already used. Please try login again.")
      return () => {
        active = false
      }
    }

    setError("")
    setIsExchanging(true)

    ;(async () => {
      try {
        const tokens = await exchangeGoogleOAuthCodeApi(code)
        if (!active) return

        sessionStorage.setItem(processedKey, "1")
        await login(tokens.accessToken, tokens.refreshToken)
        if (active) {
          navigate("/chat", { replace: true })
        }
      } catch (exchangeError) {
        if (active) {
          const message = exchangeError instanceof Error && exchangeError.message.trim()
            ? exchangeError.message
            : toOAuthErrorMessage(null)
          setError(message)
        }
      } finally {
        if (active) {
          setIsExchanging(false)
        }
      }
    })()

    return () => {
      active = false
    }
  }, [attemptKey, code, login, navigate, oauthError])

  const handleRetry = () => {
    if (code) {
      sessionStorage.removeItem(`oauth_google_processed:${code}`)
    }
    setError("")
    setAttemptKey((prev) => prev + 1)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4">
      <div className="bg-white w-full max-w-md rounded-xl shadow-xl p-8 text-center">
        <h1 className="text-2xl font-bold mb-4">Google Login</h1>

        {error ? (
          <>
            <p className="text-sm text-red-600 mb-6">{error}</p>
            <button
              onClick={handleRetry}
              className="inline-flex w-full items-center justify-center rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-700"
            >
              Retry Google Login
            </button>
            <Link
              to="/login"
              className="mt-3 inline-flex w-full items-center justify-center rounded-lg border border-gray-300 bg-white px-4 py-3 font-semibold text-gray-700 hover:bg-gray-50"
            >
              Back to Login
            </Link>
          </>
        ) : (
          <div>
            <p className="text-sm text-gray-600">Completing your Google sign-in...</p>
            {isExchanging && (
              <p className="mt-3 text-xs text-gray-500">Exchanging secure login code...</p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}