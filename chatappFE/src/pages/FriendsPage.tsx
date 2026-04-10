import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  getFriendsApi,
  getIncomingApi,
  getOutgoingApi,
  getUnreadFriendRequestCountApi,
  acceptFriendApi,
  declineFriendApi,
  cancelRequestApi,
  unfriendApi,
} from "../api/friend.service";
import { startPrivateChatApi } from "../api/room.service";

import { useUserStore } from "../store/user.store";
import { usePresenceStore } from "../store/presence.store";
import { useFriendStore } from "../store/friend.store";
import { useRooms } from "../store/room.store";
import { useChat } from "../store/chat.store";
import { FriendRow } from "../components/friend/FriendRow";
import { AddFriendPanel } from "../components/friend/AddFriendPanel";
import {
  FriendshipEventType,
  onFriendshipEvent,
  type FriendshipWsEvent,
} from "../websocket/friendship.socket";

type Tab = "online" | "all" | "pending" | "add";

const getTabFromQuery = (value: string | null): Tab => {
  if (value === "pending" || value === "requests") return "pending";
  if (value === "add") return "add";
  if (value === "all") return "all";
  return "online";
};

const unique = (items: string[]) => Array.from(new Set(items));

const getCounterpartyId = (event: FriendshipWsEvent, currentUserId: string | null) => {
  if (!currentUserId) return null;

  if (event.data.senderId && event.data.recipientId) {
    return event.data.senderId === currentUserId
      ? event.data.recipientId
      : event.data.senderId;
  }

  if (event.data.userLow && event.data.userHigh) {
    return event.data.userLow === currentUserId
      ? event.data.userHigh
      : event.data.userHigh === currentUserId
        ? event.data.userLow
        : null;
  }

  return null;
};

export default function FriendsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<Tab>(() =>
    getTabFromQuery(searchParams.get("tab"))
  );

  const [friends, setFriends] = useState<string[]>([]);
  const [incoming, setIncoming] = useState<string[]>([]);
  const [outgoing, setOutgoing] = useState<string[]>([]);
  const [activeChatUserId, setActiveChatUserId] = useState<string | null>(null);

  const currentUserId = localStorage.getItem("my_user_id");
  const fetchUsers = useUserStore((s) => s.fetchUsers);
  const userStatuses = usePresenceStore((s) => s.userStatuses);
  const unreadFriendRequests = useFriendStore((s) => s.unreadFriendRequestCount);
  const setUnreadCount = useFriendStore((s) => s.setUnreadCount);
  const setFriendStatus = useFriendStore((s) => s.setStatus);
  const { loadRooms } = useRooms();
  const { setActiveRoom } = useChat();

  /* ---------------- Derived ---------------- */

  const onlineFriends = useMemo(
    () => friends.filter((id) => (userStatuses[id] ?? "OFFLINE") !== "OFFLINE"),
    [friends, userStatuses]
  );

  /* ---------------- Load ---------------- */

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    setActiveTab(getTabFromQuery(searchParams.get("tab")));
  }, [searchParams]);

  useEffect(() => {
    const ids = [...friends, ...incoming, ...outgoing];
    if (ids.length) {
      void fetchUsers(unique(ids));
    }
  }, [fetchUsers, friends, incoming, outgoing]);

  useEffect(() => {
    const unsubscribe = onFriendshipEvent((event) => {
      const counterpartyId = getCounterpartyId(event, currentUserId);
      if (!counterpartyId) return;

      switch (event.type) {
        case FriendshipEventType.FRIEND_REQUEST_RECEIVED:
          setIncoming((prev) => unique([counterpartyId, ...prev]));
          setFriendStatus(counterpartyId, "REQUEST_RECEIVED");
          void fetchUsers([counterpartyId]);
          break;

        case FriendshipEventType.FRIEND_REQUEST_ACCEPTED:
          setIncoming((prev) => prev.filter((id) => id !== counterpartyId));
          setOutgoing((prev) => prev.filter((id) => id !== counterpartyId));
          setFriends((prev) => unique([counterpartyId, ...prev]));
          setFriendStatus(counterpartyId, "FRIENDS");
          void fetchUsers([counterpartyId]);
          break;

        case FriendshipEventType.FRIEND_REQUEST_DECLINED:
        case FriendshipEventType.FRIEND_REQUEST_CANCELLED:
          setIncoming((prev) => prev.filter((id) => id !== counterpartyId));
          setOutgoing((prev) => prev.filter((id) => id !== counterpartyId));
          setFriendStatus(counterpartyId, "NONE");
          break;

        case FriendshipEventType.FRIEND_STATUS_CHANGED:
          if (event.data.eventType === "FRIEND_UNFRIENDED") {
            setFriends((prev) => prev.filter((id) => id !== counterpartyId));
            setFriendStatus(counterpartyId, "NONE");
          }
          break;

        default:
          break;
      }
    });

    return () => {
      unsubscribe();
    };
  }, [currentUserId, fetchUsers, setFriendStatus]);

  const syncUnreadCount = async () => {
    try {
      const response = await getUnreadFriendRequestCountApi();
      setUnreadCount(response.unreadCount);
    } catch (error) {
      console.error("Failed to sync unread friend request count", error);
    }
  };

  const loadData = async () => {
    try {
      const fr = await getFriendsApi();
      const inc = await getIncomingApi();
      const out = await getOutgoingApi();

      setFriends(fr);
      setIncoming(inc);
      setOutgoing(out);
    } catch (e) {
      console.error("Failed loading friends", e);
    }
  };

  /* ---------------- Actions ---------------- */

  const handleAccept = async (id: string) => {
    await acceptFriendApi(id);
    await loadData();
    await syncUnreadCount();
  };

  const handleDecline = async (id: string) => {
    await declineFriendApi(id);
    await loadData();
    await syncUnreadCount();
  };

  const handleCancel = async (id: string) => {
    await cancelRequestApi(id);
    await loadData();
    await syncUnreadCount();
  };

  const handleRemove = async (id: string) => {
    await unfriendApi(id);
    await loadData();
  };

  const handleChat = async (id: string) => {
    if (!id || activeChatUserId) return;

    try {
      setActiveChatUserId(id);
      const room = await startPrivateChatApi(id);
      window.dispatchEvent(new Event("rooms:reload"));
      await loadRooms();
      await setActiveRoom(room.id);
      navigate("/chat");
    } catch (error) {
      console.error("Failed to open direct chat", error);
    } finally {
      setActiveChatUserId(null);
    }
  };

  const switchTab = (nextTab: Tab) => {
    setActiveTab(nextTab);

    const nextParams = new URLSearchParams(searchParams);
    if (nextTab === "online") {
      nextParams.delete("tab");
    } else {
      nextParams.set("tab", nextTab);
    }
    setSearchParams(nextParams, { replace: true });
  };

  /* ---------------- Render ---------------- */

  const renderContent = () => {
    switch (activeTab) {
      case "online":
        return onlineFriends.map((id) => (
          <FriendRow
            key={id}
            userId={id}
            variant="friend"
            onRemove={handleRemove}
            onChat={handleChat}
            isChatLaunching={activeChatUserId === id}
          />
        ));

      case "all":
        return friends.map((id) => (
          <FriendRow
            key={id}
            userId={id}
            variant="friend"
            onRemove={handleRemove}
            onChat={handleChat}
            isChatLaunching={activeChatUserId === id}
          />
        ));

      case "pending":
        return (
          <div className="space-y-6">
            <Section title={`Incoming (${incoming.length})`}>
              {incoming.map((id) => (
                <FriendRow
                  key={id}
                  userId={id}
                  variant="pending"
                  onAccept={handleAccept}
                  onDecline={handleDecline}
                />
              ))}
            </Section>

            <Section title={`Outgoing (${outgoing.length})`}>
              {outgoing.map((id) => (
                <FriendRow
                  key={id}
                  userId={id}
                  variant="pending"
                  onCancel={handleCancel}
                />
              ))}
            </Section>
          </div>
        );

      case "add":
        return (
          <div className="rounded-xl border border-gray-200 bg-white p-4">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">Find and Add Friends</h2>
            <AddFriendPanel />
          </div>
        );
    }
  };

  const totalPending = incoming.length + outgoing.length;

  return (
    <div className="h-full w-full">
      <div className="mx-auto h-full max-w-6xl rounded-2xl border border-gray-200 bg-white shadow-sm overflow-hidden">
        <div className="border-b bg-gray-50 px-5 py-4">
          <h1 className="text-2xl font-semibold text-gray-900">Friends</h1>
          <p className="mt-1 text-sm text-gray-600">
            Keep your social circle organized and jump into direct chat faster.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 p-6 lg:grid-cols-3">
          <aside className="space-y-4">
            <SummaryCard title="Total Friends" value={friends.length} subtitle="Connected people in your network" />
            <SummaryCard title="Online Now" value={onlineFriends.length} subtitle="Friends currently available" />
            <SummaryCard title="Pending Requests" value={totalPending} subtitle="Incoming and outgoing requests" badge={unreadFriendRequests} />
          </aside>

          <main className="space-y-5 lg:col-span-2">
            {/* Tabs */}
            <div className="flex flex-wrap gap-2 rounded-xl border border-gray-200 bg-white p-2">
              <TabButton
                active={activeTab === "online"}
                onClick={() => switchTab("online")}
                label={`Available (${onlineFriends.length})`}
              />

              <TabButton
                active={activeTab === "all"}
                onClick={() => switchTab("all")}
                label={`All (${friends.length})`}
              />

              <TabButton
                active={activeTab === "pending"}
                onClick={() => switchTab("pending")}
                label={`Pending (${totalPending})`}
                badgeCount={unreadFriendRequests}
              />

              <TabButton
                active={activeTab === "add"}
                onClick={() => switchTab("add")}
                label="Add Friend"
              />
            </div>

            {/* Content */}
            <div className="rounded-xl border border-gray-200 bg-gray-50 p-4">
              <div className="space-y-3">{renderContent()}</div>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}

/* ---------------- UI Helpers ---------------- */

function TabButton({
  active,
  onClick,
  label,
  badgeCount = 0,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  badgeCount?: number;
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-lg px-4 py-2 text-sm font-medium transition ${
        active
          ? "bg-blue-600 text-white shadow-sm"
          : "text-gray-700 hover:bg-gray-100"
      }`}
    >
      <span className="inline-flex items-center gap-2">
        <span>{label}</span>
        {badgeCount > 0 && (
          <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-semibold text-white">
            {badgeCount > 99 ? "99+" : badgeCount}
          </span>
        )}
      </span>
    </button>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-600">{title}</h2>
      <div className="space-y-2">{children}</div>
    </div>
  );
}

function SummaryCard({
  title,
  value,
  subtitle,
  badge = 0,
}: {
  title: string;
  value: number;
  subtitle: string;
  badge?: number;
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{title}</p>
        {badge > 0 && (
          <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-semibold text-white">
            {badge > 99 ? "99+" : badge}
          </span>
        )}
      </div>
      <p className="mt-2 text-3xl font-semibold text-gray-900">{value}</p>
      <p className="mt-1 text-xs text-gray-500">{subtitle}</p>
    </div>
  );
}