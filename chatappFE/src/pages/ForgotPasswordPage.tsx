import { useState } from "react"
import { Link } from "react-router-dom"
import { forgotPasswordApi } from "../api/auth.service"

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("")
  const [emailError, setEmailError] = useState("")
  const [submitted, setSubmitted] = useState(false)
  const [loading, setLoading] = useState(false)

  const validateEmail = (value: string) => {
    if (!value.trim()) return "Email is required"
    if (!emailRegex.test(value)) return "Invalid email format"
    return ""
  }

  const handleSubmit = async () => {
    const err = validateEmail(email)
    setEmailError(err)
    if (err) return

    setLoading(true)
    try {
      await forgotPasswordApi(email.trim())
      setSubmitted(true)
    } catch {
      // Silently succeed — never reveal whether email exists
      setSubmitted(true)
    } finally {
      setLoading(false)
    }
  }

  if (submitted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="bg-white w-[380px] rounded-xl shadow-xl p-8 text-center">
          <div className="text-4xl mb-4">📧</div>
          <h1 className="text-xl font-bold mb-2">Check your email</h1>
          <p className="text-gray-500 text-sm mb-6">
            If an account exists for <span className="font-medium">{email}</span>, a password reset link has been sent.
            The link expires in 1 hour.
          </p>
          <Link
            to="/login"
            className="text-indigo-600 font-semibold hover:underline text-sm"
          >
            Back to login
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="bg-white w-[380px] rounded-xl shadow-xl p-8">
        <h1 className="text-2xl font-bold text-center mb-2">Forgot Password</h1>
        <p className="text-gray-500 text-sm text-center mb-6">
          Enter your email and we'll send you a reset link.
        </p>

        {/* EMAIL */}
        <div className="relative mb-5">
          <input
            type="email"
            value={email}
            onChange={(e) => { setEmail(e.target.value); setEmailError("") }}
            placeholder="Email"
            className={`w-full p-3 rounded-lg border outline-none transition ${
              emailError ? "border-red-500" : "focus:ring-2 focus:ring-indigo-500"
            }`}
          />
          {emailError && (
            <div className="absolute left-0 top-full mt-2">
              <div className="bg-red-500 text-white text-xs px-3 py-2 rounded-lg shadow-lg relative">
                {emailError}
                <div className="absolute -top-1 left-3 w-2 h-2 bg-red-500 rotate-45"></div>
              </div>
            </div>
          )}
        </div>

        <button
          onClick={handleSubmit}
          disabled={loading}
          className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white py-3 rounded-lg font-semibold mt-2"
        >
          {loading ? "Sending..." : "Send Reset Link"}
        </button>

        <p className="text-center text-sm mt-6">
          Remember your password?{" "}
          <Link to="/login" className="text-indigo-600 font-semibold hover:underline">
            Back to login
          </Link>
        </p>
      </div>
    </div>
  )
}
