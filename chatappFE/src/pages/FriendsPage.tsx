import { useEffect, useMemo, useState } from "react";
import {
  getFriendsApi,
  getIncomingApi,
  getOutgoingApi,
  acceptFriendApi,
  declineFriendApi,
  cancelRequestApi,
  unfriendApi,
} from "../api/friend.service";

import { useUserStore } from "../store/user.store";
import { usePresenceStore } from "../store/presence.store";
import { FriendRow } from "../components/friend/FriendRow";
import { AddFriendPanel } from "../components/friend/AddFriendPanel";
import PresenceStatusControl from "../components/presence/PresenceStatusControl";

type Tab = "online" | "all" | "pending" | "add";

export default function FriendsPage() {
  const [activeTab, setActiveTab] = useState<Tab>("online");

  const [friends, setFriends] = useState<string[]>([]);
  const [incoming, setIncoming] = useState<string[]>([]);
  const [outgoing, setOutgoing] = useState<string[]>([]);

  const fetchUsers = useUserStore((s) => s.fetchUsers);
  const userStatuses = usePresenceStore((s) => s.userStatuses);

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
    const ids = [...friends, ...incoming, ...outgoing];
    if (ids.length) fetchUsers(ids);
  }, [friends, incoming, outgoing]);

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
    loadData();
  };

  const handleDecline = async (id: string) => {
    await declineFriendApi(id);
    loadData();
  };

  const handleCancel = async (id: string) => {
    await cancelRequestApi(id);
    loadData();
  };

  const handleRemove = async (id: string) => {
    await unfriendApi(id);
    loadData();
  };

  const handleChat = (id: string) => {
    console.log("Open chat with", id);
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
          />
        ));

      case "pending":
        return (
          <div className="space-y-8">
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
        return <AddFriendPanel />;
    }
  };

  return (
    <div className="h-full w-full">
      <div className="max-w-5xl mx-auto">
        <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
          <h1 className="text-2xl font-bold">Friends</h1>
          <PresenceStatusControl />
        </div>

        {/* Tabs */}
        <div className="flex gap-3 mb-6 flex-wrap">
          <TabButton
            active={activeTab === "online"}
            onClick={() => setActiveTab("online")}
            label={`Available (${onlineFriends.length})`}
          />

          <TabButton
            active={activeTab === "all"}
            onClick={() => setActiveTab("all")}
            label={`All (${friends.length})`}
          />

          <TabButton
            active={activeTab === "pending"}
            onClick={() => setActiveTab("pending")}
            label={`Pending (${incoming.length + outgoing.length})`}
          />

          <TabButton
            active={activeTab === "add"}
            onClick={() => setActiveTab("add")}
            label="Add Friend"
          />
        </div>

        {/* Content */}
        <div className="space-y-3">{renderContent()}</div>
      </div>
    </div>
  );
}

/* ---------------- UI Helpers ---------------- */

function TabButton({
  active,
  onClick,
  label,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 rounded-lg text-sm transition ${
        active
          ? "bg-blue-500 text-white"
          : "bg-gray-200 hover:bg-gray-300"
      }`}
    >
      {label}
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
    <div>
      <h2 className="font-semibold mb-3">{title}</h2>
      <div className="space-y-2">{children}</div>
    </div>
  );
}