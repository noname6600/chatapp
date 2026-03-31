import { useState } from "react";
import { loginApi, registerApi } from "../api/auth.service";
import { useAuth } from "../hooks/useAuth";
import { useNavigate } from "react-router-dom";

type Mode = "login" | "register";

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function AuthPage() {

  const { login } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState<Mode>("login");

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const [usernameError, setUsernameError] = useState("");
  const [formError, setFormError] = useState("");

  const validateEmailBlur = (value: string) => {

    if (value === "") return "";

    if (!value.includes("@")) return "Email must contain @";

    if (!emailRegex.test(value)) return "Invalid email format";

    return "";
  };

  const validateEmailSubmit = (value: string) => {

    if (value === "") return "Email cannot be empty";

    if (!value.includes("@")) return "Email must contain @";

    if (!emailRegex.test(value)) return "Invalid email format";

    return "";
  };

  const handleUsernameBlur = () => {
    setUsernameError(validateEmailBlur(username));
  };

  const handleUsernameFocus = () => {
    setUsernameError("");
    setFormError("");
  };

  const handleSubmit = async () => {

    setFormError("");

    const error = validateEmailSubmit(username);
    setUsernameError(error);

    if (error) return;

    try {

      const tokens =
        mode === "login"
          ? await loginApi({ username, password })
          : await registerApi({ username, password });

      login(tokens.accessToken, tokens.refreshToken);

      navigate("/chat");

    } catch (err) {

      if (err instanceof Error) {
        setFormError(err.message);
      }

    }
  };

  const handleGoogleLogin = () => {
    window.location.href =
      "http://localhost:8080/oauth2/authorization/google";
  };

  return (

    <div className="min-h-screen flex items-center justify-center bg-gray-100">

      <div className="bg-white w-[380px] rounded-xl shadow-xl p-8">

        <h1 className="text-2xl font-bold text-center mb-6">
          {mode === "login" ? "Login" : "Register"}
        </h1>

        {/* FORM ERROR */}
        {formError && (
          <div className="bg-red-100 border border-red-300 text-red-700 text-sm p-3 rounded mb-4">
            {formError}
          </div>
        )}

        {/* EMAIL */}
        <div className="relative mb-5">

          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            onBlur={handleUsernameBlur}
            onFocus={handleUsernameFocus}
            placeholder="Email"
            className={`w-full p-3 rounded-lg border outline-none transition
              ${
                usernameError
                  ? "border-red-500"
                  : "focus:ring-2 focus:ring-indigo-500"
              }`}
          />

          {usernameError && (
            <div className="absolute left-0 top-full mt-2 animate-fade">

              <div className="bg-red-500 text-white text-xs px-3 py-2 rounded-lg shadow-lg relative">

                {usernameError}

                <div className="absolute -top-1 left-3 w-2 h-2 bg-red-500 rotate-45"></div>

              </div>

            </div>
          )}

        </div>

        {/* PASSWORD */}
        <input
          type="password"
          placeholder="Password"
          className="w-full p-3 border rounded-lg mb-4 focus:ring-2 focus:ring-indigo-500 outline-none"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {/* BUTTON */}
        <button
          onClick={handleSubmit}
          className="w-full bg-indigo-600 hover:bg-indigo-700 text-white py-3 rounded-lg font-semibold"
        >
          {mode === "login" ? "Login" : "Register"}
        </button>

        {/* DIVIDER */}
        <div className="text-center text-gray-400 my-4">OR</div>

        {/* GOOGLE LOGIN */}
        <button
          onClick={handleGoogleLogin}
          className="w-full border rounded-lg py-3 flex items-center justify-center gap-2 hover:bg-gray-50"
        >
          <img
            src="https://www.svgrepo.com/show/475656/google-color.svg"
            className="w-5 h-5"
          />
          Continue with Google
        </button>

        {/* SWITCH */}
        <p className="text-center text-sm mt-6">

          {mode === "login"
            ? "Don't have an account?"
            : "Already have an account?"}

          <span
            className="text-indigo-600 font-semibold ml-1 cursor-pointer"
            onClick={() =>
              setMode(mode === "login" ? "register" : "login")
            }
          >
            {mode === "login" ? "Register" : "Login"}
          </span>

        </p>

      </div>

    </div>
  );
}