import AppRoutes from "./routes/AppRoutes"
import { AuthProvider } from "./store/auth.store"
import { ChatProvider } from "./store/chat.store"
import { NotificationProvider } from "./store/notification.store"
import { RoomProvider } from "./store/room.store"

function App() {

  return (

    <AuthProvider>

      <NotificationProvider>

        <ChatProvider>

          <RoomProvider>

            <AppRoutes />

          </RoomProvider>

        </ChatProvider>

      </NotificationProvider>

    </AuthProvider>

  )

}

export default App