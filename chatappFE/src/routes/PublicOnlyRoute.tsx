import { Navigate, Outlet } from "react-router-dom"
import { useAuth } from "../hooks/useAuth"

const PublicOnlyRoute = () => {
  const { accessToken, isInitializing, isBootstrapping } = useAuth()

  if (isInitializing || isBootstrapping) {
    return <div className="p-6">Loading...</div>
  }

  return accessToken ? <Navigate to="/chat" replace /> : <Outlet />
}

export default PublicOnlyRoute