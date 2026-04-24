/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Room } from "../types/room";
import ChatPageLayout from "./ChatPageLayout";

const mocks = vi.hoisted(() => {
  const chatListeners = new Set<() => void>();
  const roomListeners = new Set<() => void>();
  let chatRevision = 0;
  let roomRevision = 0;

  const emitChat = () => {
    chatRevision += 1;
    chatListeners.forEach((listener) => listener());
  };

  const emitRooms = () => {
    roomRevision += 1;
    roomListeners.forEach((listener) => listener());
  };

  const chatState = {
    activeRoomId: "room-1" as string | null,
    upsertMessage: vi.fn(),
    setActiveRoom: vi.fn(async (roomId: string) => {
      chatState.activeRoomId = roomId || null;
      emitChat();
    }),
  };

  const roomState = {
    roomsById: {} as Record<string, Room>,
    roomOrder: [] as string[],
    loadRooms: vi.fn(async () => {}),
    removeRoom: vi.fn((roomId: string) => {
      const nextRooms = { ...roomState.roomsById };
      delete nextRooms[roomId];
      roomState.roomsById = nextRooms;
      roomState.roomOrder = roomState.roomOrder.filter((id) => id !== roomId);
      emitRooms();
    }),
  };

  return {
    chatListeners,
    roomListeners,
    chatState,
    roomState,
    emitChat,
    emitRooms,
    getChatRevision: () => chatRevision,
    getRoomRevision: () => roomRevision,
    leaveRoomApi: vi.fn(),
    startPrivateChatApi: vi.fn(),
    sendMessageApi: vi.fn(),
    fetchRoomNotificationMode: vi.fn(async () => "NO_RESTRICT"),
    joinPresenceRoom: vi.fn(),
    leavePresenceRoom: vi.fn(),
    onPresenceOpen: vi.fn(() => () => {}),
  };
});

const makeRoom = (overrides: Partial<Room> = {}): Room => ({
  id: overrides.id ?? "room-1",
  type: overrides.type ?? "GROUP",
  name: overrides.name ?? "Alpha Group",
  avatarUrl: overrides.avatarUrl ?? null,
  createdBy: overrides.createdBy ?? "me",
  createdAt: overrides.createdAt ?? "2026-04-16T00:00:00.000Z",
  myRole: overrides.myRole ?? "OWNER",
  unreadCount: overrides.unreadCount ?? 0,
  latestMessageAt: overrides.latestMessageAt ?? "2026-04-16T01:00:00.000Z",
  otherUserId: overrides.otherUserId ?? null,
  lastMessage: overrides.lastMessage ?? {
    id: "msg-1",
    senderId: "user-2",
    senderName: "Alex",
    content: "Latest preview",
    createdAt: "2026-04-16T01:00:00.000Z",
  },
});

vi.mock("../store/chat.store", async () => {
  const React = await import("react");

  return {
    useChat: () => {
      React.useSyncExternalStore(
        (listener) => {
          mocks.chatListeners.add(listener);
          return () => {
            mocks.chatListeners.delete(listener);
          };
        },
        () => mocks.getChatRevision()
      );

      return mocks.chatState;
    },
  };
});

vi.mock("../store/room.store", async () => {
  const React = await import("react");

  return {
    useRooms: () => {
      React.useSyncExternalStore(
        (listener) => {
          mocks.roomListeners.add(listener);
          return () => {
            mocks.roomListeners.delete(listener);
          };
        },
        () => mocks.getRoomRevision()
      );

      return mocks.roomState;
    },
  };
});

vi.mock("../store/notification.store", () => ({
  useNotifications: () => ({
    fetchRoomNotificationMode: mocks.fetchRoomNotificationMode,
  }),
}));

vi.mock("../api/room.service", () => ({
  leaveRoomApi: mocks.leaveRoomApi,
  startPrivateChatApi: mocks.startPrivateChatApi,
}));

vi.mock("../api/chat.service", () => ({
  sendMessageApi: mocks.sendMessageApi,
}));

vi.mock("../websocket/presence.socket", () => ({
  joinPresenceRoom: mocks.joinPresenceRoom,
  leavePresenceRoom: mocks.leavePresenceRoom,
  onPresenceOpen: mocks.onPresenceOpen,
}));

vi.mock("../components/rooms/RoomList", async () => {
  const React = await import("react");

  return {
    default: () => {
      React.useSyncExternalStore(
        (listener) => {
          mocks.roomListeners.add(listener);
          return () => {
            mocks.roomListeners.delete(listener);
          };
        },
        () => mocks.getRoomRevision()
      );

      return (
        <div data-testid="room-list">
          {mocks.roomState.roomOrder.map((roomId) => {
            const room = mocks.roomState.roomsById[roomId];
            if (!room) return null;

            return (
              <div key={room.id} data-testid={`room-item-${room.id}`}>
                {room.name}
              </div>
            );
          })}
        </div>
      );
    },
  };
});

vi.mock("../components/chat/RoomHeader", () => ({
  default: ({
    room,
    onLeave,
  }: {
    room: Room | null;
    onLeave?: () => void;
  }) =>
    room ? (
      <div data-testid="room-header">
        <span>{room.name}</span>
        <button type="button" onClick={onLeave}>
          Open leave modal
        </button>
      </div>
    ) : null,
}));

vi.mock("../components/chat/MessageList", () => ({
  default: ({ roomId }: { roomId: string }) => (
    <div data-testid="message-list">Messages for {roomId}</div>
  ),
}));

vi.mock("../components/chat/MessageInput", () => ({
  default: ({ roomId }: { roomId: string }) => (
    <div data-testid="message-input">Input for {roomId}</div>
  ),
}));

vi.mock("../components/presence/TypingIndicator", () => ({
  default: ({ roomId }: { roomId: string }) => (
    <div data-testid="typing-indicator">Typing for {roomId}</div>
  ),
}));

vi.mock("../components/chat/RoomMembersSidebar", () => ({
  default: ({ roomId }: { roomId: string }) => (
    <div data-testid="members-sidebar">Members for {roomId}</div>
  ),
}));

vi.mock("../components/rooms/RoomSettingsModal", () => ({
  default: () => null,
}));

vi.mock("../components/rooms/InviteMembersModal", () => ({
  default: () => null,
}));

describe("ChatPageLayout leave flow", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.chatState.activeRoomId = "room-1";
    mocks.roomState.roomsById = {
      "room-1": makeRoom(),
    };
    mocks.roomState.roomOrder = ["room-1"];

    mocks.roomState.loadRooms.mockResolvedValue(undefined);
    mocks.leaveRoomApi.mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
  });

  it("removes the room from the visible room list after a successful leave", async () => {
    render(<ChatPageLayout />);

    expect(screen.getByTestId("room-item-room-1").textContent).toContain("Alpha Group");

    fireEvent.click(screen.getByRole("button", { name: "Open leave modal" }));
    fireEvent.click(screen.getByRole("button", { name: "Leave Group" }));

    await waitFor(() => {
      expect(screen.queryByTestId("room-item-room-1")).toBeNull();
    });

    expect(mocks.roomState.removeRoom).toHaveBeenCalledWith("room-1");
    expect(mocks.roomState.loadRooms).toHaveBeenCalledTimes(1);
  });

  it("exits the active room UI and shows the empty state after a successful leave", async () => {
    render(<ChatPageLayout />);

    expect(screen.getByTestId("room-header")).toBeTruthy();
    expect(screen.getByTestId("message-list").textContent).toContain("Messages for room-1");
    expect(screen.getByTestId("typing-indicator").textContent).toContain("Typing for room-1");
    expect(screen.getByTestId("message-input").textContent).toContain("Input for room-1");
    expect(screen.getByTestId("members-sidebar").textContent).toContain("Members for room-1");

    fireEvent.click(screen.getByRole("button", { name: "Open leave modal" }));
    fireEvent.click(screen.getByRole("button", { name: "Leave Group" }));

    await waitFor(() => {
      expect(screen.getByText("Select a room to start chatting")).toBeTruthy();
    });

    expect(screen.queryByTestId("room-header")).toBeNull();
    expect(screen.queryByTestId("message-list")).toBeNull();
    expect(screen.queryByTestId("typing-indicator")).toBeNull();
    expect(screen.queryByTestId("message-input")).toBeNull();
    expect(screen.queryByTestId("members-sidebar")).toBeNull();
    expect(mocks.leavePresenceRoom).toHaveBeenCalledWith("room-1");
    expect(mocks.chatState.activeRoomId).toBeNull();
  });

  it("keeps the room visible and shows an error when leave fails", async () => {
    mocks.leaveRoomApi.mockRejectedValue(new Error("Leave failed"));

    render(<ChatPageLayout />);

    fireEvent.click(screen.getByRole("button", { name: "Open leave modal" }));
    fireEvent.click(screen.getByRole("button", { name: "Leave Group" }));

    await waitFor(() => {
      expect(screen.getByText("Leave failed")).toBeTruthy();
    });

    expect(screen.getByTestId("room-item-room-1").textContent).toContain("Alpha Group");
    expect(screen.getByTestId("message-list").textContent).toContain("Messages for room-1");
    expect(screen.queryByText("Select a room to start chatting")).toBeNull();
    expect(mocks.roomState.removeRoom).not.toHaveBeenCalled();
    expect(mocks.roomState.loadRooms).not.toHaveBeenCalled();
  });
});
