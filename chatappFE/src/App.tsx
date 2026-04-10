import AppRoutes from "./routes/AppRoutes"
import { AuthProvider } from "./store/auth.store"
import { ChatProvider } from "./store/chat.store"
import { NotificationProvider } from "./store/notification.store"
import { RoomProvider } from "./store/room.store"
import { useFriendshipInitialization } from "./store/friendship.provider"

function AppContent() {
  // Initialize friendship socket and load unread count
  useFriendshipInitialization()

  return <AppRoutes />
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