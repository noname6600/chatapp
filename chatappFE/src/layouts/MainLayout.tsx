import { Outlet } from "react-router-dom";
import Sidebar from "../components/layout/Sidebar";
import UserPopup from "../components/user/UserPopup";
import ActiveCallBar from "../components/voice/ActiveCallBar";
import VoiceRoomActiveBar from "../components/voice/VoiceRoomActiveBar";
import VoiceReconnectBar from "../components/voice/VoiceReconnectBar";

const MainLayout = () => {
  return (
    <div className="h-screen flex flex-col bg-gray-100 overflow-hidden">
      <ActiveCallBar />
      <VoiceRoomActiveBar />
      <VoiceReconnectBar />

      <div className="flex-1 flex min-h-0">
        <Sidebar />

        <div className="flex-1 flex flex-col min-w-0">
          <main className="flex-1 min-h-0 overflow-hidden">
            <Outlet />
          </main>
        </div>

        <UserPopup />
      </div>
    </div>
  );
};

export default MainLayout;