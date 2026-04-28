import { useEffect, useState } from "react"
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
  const { login } = useAuth()
  const [error, setError] = useState("")

  useEffect(() => {
    let cancelled = false

    const oauthError = searchParams.get("oauth_error") || searchParams.get("error")
    const code = searchParams.get("code")

    if (oauthError) {
      setError(toOAuthErrorMessage(oauthError))
      return () => {
        cancelled = true
      }
    }

    if (!code) {
      setError(toOAuthErrorMessage(null))
      return () => {
        cancelled = true
      }
    }

    ;(async () => {
      try {
        const tokens = await exchangeGoogleOAuthCodeApi(code)
        await login(tokens.accessToken, tokens.refreshToken)
        if (!cancelled) {
          navigate("/chat", { replace: true })
        }
      } catch (exchangeError) {
        if (!cancelled) {
          const message = exchangeError instanceof Error && exchangeError.message.trim()
            ? exchangeError.message
            : toOAuthErrorMessage(null)
          setError(message)
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [login, navigate, searchParams])

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4">
      <div className="bg-white w-full max-w-md rounded-xl shadow-xl p-8 text-center">
        <h1 className="text-2xl font-bold mb-4">Google Login</h1>

        {error ? (
          <>
            <p className="text-sm text-red-600 mb-6">{error}</p>
            <Link
              to="/login"
              className="inline-flex items-center justify-center rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-700"
            >
              Back to Login
            </Link>
          </>
        ) : (
          <p className="text-sm text-gray-600">Completing your Google sign-in...</p>
        )}
      </div>
    </div>
  )
}