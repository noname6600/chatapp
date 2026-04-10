import { useState, useEffect } from "react"
import { useNavigate, useSearchParams, Link } from "react-router-dom"
import { resetPasswordApi } from "../api/auth.service"

const PASSWORD_RULES = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const token = searchParams.get("token") ?? ""

  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (!token) {
      setError("Invalid or missing reset link. Please request a new one.")
    }
  }, [token])

  const validate = () => {
    if (!newPassword) return "New password is required"
    if (!PASSWORD_RULES.test(newPassword))
      return "Password must be 8+ characters with uppercase, lowercase, and a number"
    if (newPassword !== confirmPassword) return "Passwords do not match"
    return ""
  }

  const handleSubmit = async () => {
    const err = validate()
    setError(err)
    if (err || !token) return

    setLoading(true)
    try {
      await resetPasswordApi(token, newPassword)
      setSuccess(true)
      setTimeout(() => navigate("/login"), 3000)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Reset failed. The link may have expired.")
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="bg-white w-[380px] rounded-xl shadow-xl p-8 text-center">
          <div className="text-4xl mb-4">✅</div>
          <h1 className="text-xl font-bold mb-2">Password Reset</h1>
          <p className="text-gray-500 text-sm mb-4">
            Your password has been updated. Redirecting you to login…
          </p>
          <Link to="/login" className="text-indigo-600 font-semibold hover:underline text-sm">
            Go to login
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="bg-white w-[380px] rounded-xl shadow-xl p-8">
        <h1 className="text-2xl font-bold text-center mb-2">Reset Password</h1>
        <p className="text-gray-500 text-sm text-center mb-6">
          Enter your new password below.
        </p>

        {error && (
          <div className="bg-red-100 border border-red-300 text-red-700 text-sm p-3 rounded mb-4">
            {error}
          </div>
        )}

        <input
          type="password"
          placeholder="New password"
          value={newPassword}
          onChange={(e) => { setNewPassword(e.target.value); setError("") }}
          className="w-full p-3 border rounded-lg mb-4 focus:ring-2 focus:ring-indigo-500 outline-none"
        />
        <input
          type="password"
          placeholder="Confirm new password"
          value={confirmPassword}
          onChange={(e) => { setConfirmPassword(e.target.value); setError("") }}
          className="w-full p-3 border rounded-lg mb-4 focus:ring-2 focus:ring-indigo-500 outline-none"
        />

        <p className="text-xs text-gray-400 mb-5">
          8+ characters, with uppercase, lowercase, and a number.
        </p>

        <button
          onClick={handleSubmit}
          disabled={loading || !token}
          className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white py-3 rounded-lg font-semibold"
        >
          {loading ? "Resetting..." : "Reset Password"}
        </button>

        <p className="text-center text-sm mt-6">
          <Link to="/login" className="text-indigo-600 font-semibold hover:underline">
            Back to login
          </Link>
        </p>
      </div>
    </div>
  )
}
