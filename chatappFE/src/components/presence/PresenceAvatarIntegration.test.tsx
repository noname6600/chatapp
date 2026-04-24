// @vitest-environment jsdom

import { describe, expect, it } from "vitest"
import { render, waitFor } from "@testing-library/react"

import UserAvatar from "../user/UserAvatar"
import ProfileIdentityCard from "../profile/ProfileIdentityCard"
import { usePresenceStore } from "../../store/presence.store"

describe("Presence avatar propagation", () => {
  it("updates chat and profile avatar frames when presence status changes", async () => {
    localStorage.setItem("my_user_id", "me")
    usePresenceStore.getState().clearAllOnline()

    const view = render(
      <div>
        <UserAvatar userId="u1" avatar={null} size={32} />
        <ProfileIdentityCard
          presentation={{
            displayName: "User One",
            username: "userone",
            avatarUrl: "/default-avatar.png",
            aboutMe: "",
            backgroundColor: "#111111",
          }}
          userId="u1"
          avatarSize={80}
        />
      </div>
    )

    usePresenceStore.getState().setUserStatus("u1", "ONLINE")

    await waitFor(() => {
      expect(view.container.querySelectorAll(".bg-green-500").length).toBeGreaterThanOrEqual(2)
    })

    usePresenceStore.getState().setUserStatus("u1", "AWAY")

    await waitFor(() => {
      expect(view.container.querySelectorAll(".bg-amber-400").length).toBeGreaterThanOrEqual(2)
    })

    usePresenceStore.getState().setUserStatus("u1", "OFFLINE")

    await waitFor(() => {
      expect(view.container.querySelectorAll(".bg-gray-500").length).toBeGreaterThanOrEqual(2)
    })
  })

  it("keeps avatar content-box sizing stable between fallback and resolved avatar states", () => {
    const fallbackView = render(
      <div>
        <UserAvatar userId="u1" avatar={null} size={32} />
        <ProfileIdentityCard
          presentation={{
            displayName: "User One",
            username: "userone",
            avatarUrl: "/default-avatar.png",
            aboutMe: "",
            backgroundColor: "#111111",
          }}
          userId="u1"
          avatarSize={80}
        />
      </div>
    )

    const resolvedView = render(
      <div>
        <UserAvatar userId="u1" avatar="/avatars/generated-u1.png" size={32} />
        <ProfileIdentityCard
          presentation={{
            displayName: "User One",
            username: "userone",
            avatarUrl: "/avatars/generated-u1.png",
            aboutMe: "",
            backgroundColor: "#111111",
          }}
          userId="u1"
          avatarSize={80}
        />
      </div>
    )

    const fallbackImages = fallbackView.container.querySelectorAll("img")
    const resolvedImages = resolvedView.container.querySelectorAll("img")

    expect((fallbackImages[0]?.parentElement as HTMLElement).style.width).toBe("32px")
    expect((resolvedImages[0]?.parentElement as HTMLElement).style.width).toBe("32px")
    expect((fallbackImages[0]?.parentElement as HTMLElement).style.height).toBe("32px")
    expect((resolvedImages[0]?.parentElement as HTMLElement).style.height).toBe("32px")

    expect((fallbackImages[1]?.parentElement as HTMLElement).style.width).toBe("80px")
    expect((resolvedImages[1]?.parentElement as HTMLElement).style.width).toBe("80px")
    expect((fallbackImages[1]?.parentElement as HTMLElement).style.height).toBe("80px")
    expect((resolvedImages[1]?.parentElement as HTMLElement).style.height).toBe("80px")
  })
})
