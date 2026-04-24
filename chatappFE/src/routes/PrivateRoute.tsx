import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

const PrivateRoute = () => {
  const { accessToken, isInitializing, isBootstrapping } = useAuth();

  if (isInitializing || isBootstrapping) {
    return <div className="p-6">Loading...</div>;
  }

  return accessToken ? <Outlet /> : <Navigate to="/login" replace />;
};

export default PrivateRoute;
