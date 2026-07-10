import AppRoutes from "./routes/AppRoutes"
import { AuthProvider } from "./store/auth.store"
import { ChatProvider } from "./store/chat.store"
import { NotificationProvider } from "./store/notification.store"
import { RoomProvider } from "./store/room.store"
import { useFriendshipInitialization } from "./store/friendship.provider"
import IncomingCallOverlay from "./components/voice/IncomingCallOverlay"
import OutgoingCallOverlay from "./components/voice/OutgoingCallOverlay"
import ActiveCallBar from "./components/voice/ActiveCallBar"
// Side-effect import — registers call event handlers on the realtime socket
import "./websocket/call.socket"

function AppContent() {
  // Initialize friendship socket and load unread count
  useFriendshipInitialization()

  return (
    <>
      <ActiveCallBar />
      <IncomingCallOverlay />
      <OutgoingCallOverlay />
      <AppRoutes />
    </>
  )
}

function App() {

  return (

    <AuthProvider>

      <NotificationProvider>

        <ChatProvider>

          <RoomProvider>

            <AppContent />

          </RoomProvider>

        </ChatProvider>

      </NotificationProvider>

    </AuthProvider>

  )

}

export default App