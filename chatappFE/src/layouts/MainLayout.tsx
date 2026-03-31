import { Outlet } from "react-router-dom";
import Sidebar from "../components/layout/Sidebar";
import UserPopup from "../components/user/UserPopup";

const MainLayout = () => {
  return (
    <div className="h-screen flex bg-gray-100 overflow-hidden">
      <Sidebar />

      <div className="flex-1 flex flex-col min-w-0">
        <main className="flex-1 min-h-0 overflow-hidden">
          <Outlet />
        </main>
      </div>

      <UserPopup />
    </div>
  );
};

export default MainLayout;