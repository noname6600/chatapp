import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import AuthPage from "../pages/AuthPage";
import FriendsPage from "../pages/FriendsPage";
import NotFoundPage from "../pages/NotFoundPage";
import MainLayout from "../layouts/MainLayout";
import PrivateRoute from "./PrivateRoute";
import ChatPageLayout from "../layouts/ChatPageLayout";
import ProfileSettingsPage from "../pages/ProfileSettingsPage";
import ForgotPasswordPage from "../pages/ForgotPasswordPage";
import ResetPasswordPage from "../pages/ResetPasswordPage";
import VerifyEmailPage from "../pages/VerifyEmailPage";


const AppRoutes: React.FC = () => {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login" element={<AuthPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/auth/reset-password" element={<ResetPasswordPage />} />
      <Route path="/auth/verify-email" element={<VerifyEmailPage />} />

      {/* Private */}
      <Route element={<PrivateRoute />}>
        <Route element={<MainLayout />}>
          <Route path="/" element={<Navigate to="/chat" replace />} />
          <Route path="/chat" element={<ChatPageLayout />} />
          <Route path="/friends" element={<FriendsPage />} />
          <Route path="/settings" element={<ProfileSettingsPage />} />
          <Route path="/me" element={<Navigate to="/settings" replace />} />
        </Route>
      </Route>

      {/* 404 */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
};

export default AppRoutes;
