import { useState, useEffect } from "react"
import { useSearchParams, Link } from "react-router-dom"
import { confirmEmailVerificationApi } from "../api/auth.service"

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token") ?? ""

  const [status, setStatus] = useState<"loading" | "success" | "error">("loading")
  const [errorMessage, setErrorMessage] = useState("")

  useEffect(() => {
    if (!token) {
      setStatus("error")
      setErrorMessage("Invalid or missing verification link.")
      return
    }

    confirmEmailVerificationApi(token)
      .then(() => setStatus("success"))
      .catch((e) => {
        setStatus("error")
        setErrorMessage(e instanceof Error ? e.message : "Verification failed. The link may have expired.")
      })
  }, [token])

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="bg-white w-[380px] rounded-xl shadow-xl p-8 text-center">
        {status === "loading" && (
          <>
            <div className="text-4xl mb-4">⏳</div>
            <h1 className="text-xl font-bold mb-2">Verifying your email…</h1>
          </>
        )}

        {status === "success" && (
          <>
            <div className="text-4xl mb-4">✅</div>
            <h1 className="text-xl font-bold mb-2">Email Verified!</h1>
            <p className="text-gray-500 text-sm mb-6">
              Your email address has been successfully verified.
            </p>
            <Link
              to="/settings"
              className="text-indigo-600 font-semibold hover:underline text-sm"
            >
              Go to Settings
            </Link>
          </>
        )}

        {status === "error" && (
          <>
            <div className="text-4xl mb-4">❌</div>
            <h1 className="text-xl font-bold mb-2">Verification Failed</h1>
            <p className="text-red-500 text-sm mb-6">{errorMessage}</p>
            <Link
              to="/settings"
              className="text-indigo-600 font-semibold hover:underline text-sm"
            >
              Go to Settings to resend
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
