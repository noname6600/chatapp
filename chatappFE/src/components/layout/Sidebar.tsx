import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useState, useEffect } from "react";
import { Menu, X, ChevronLeft, ChevronRight } from "lucide-react";
import { updateMyPresenceApi } from "../../api/presence.service";
import { usePresenceStore } from "../../store/presence.store";
import type { PresenceStatus } from "../../types/presence";
import { getStatusDotClass } from "../../utils/presenceStatus";
import { useAuth } from "../../hooks/useAuth";
import { useFriendStore } from "../../store/friend.store";
import UserAvatar from "../user/UserAvatar";
import NotificationBell from "../notifications/NotificationBell";

const menu = [
  { to: "/chat", label: "Chat" },
  { to: "/friends", label: "Friends" },
  { to: "/settings", label: "Settings" },
];

const statusOptions: PresenceStatus[] = ["ONLINE", "AWAY", "OFFLINE"];

const Sidebar = () => {
  const { logout, currentUser, userId } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const unreadFriendRequests = useFriendStore((s) => s.unreadFriendRequestCount);

  const selfPresence = usePresenceStore((s) => s.selfPresence);
  const setSelfPresence = usePresenceStore((s) => s.setSelfPresence);
  const setUserStatus = usePresenceStore((s) => s.setUserStatus);

  const [isStatusMenuOpen, setIsStatusMenuOpen] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);

  // Responsive sidebar state
  const [isOpen, setIsOpen] = useState(true);
  const [isMobile, setIsMobile] = useState(false);

  // Desktop collapse state (persists across navigation)
  const [isCollapsed, setIsCollapsed] = useState(() => {
    const saved = localStorage.getItem("sidebarCollapsed");
    return saved ? JSON.parse(saved) : false;
  });

  const effectiveStatus: PresenceStatus = selfPresence?.effectiveStatus ?? "OFFLINE";

  const handleStatusChange = async (nextStatus: PresenceStatus) => {
    try {
      setStatusSaving(true);
      const nextPresence = await updateMyPresenceApi({ mode: "MANUAL", status: nextStatus });
      setSelfPresence(nextPresence);
      if (userId) {
        setUserStatus(userId, nextPresence.effectiveStatus);
      }
      setIsStatusMenuOpen(false);
    } catch (err) {
      console.error("Failed to update presence", err);
    } finally {
      setStatusSaving(false);
    }
  };

  // Detect viewport changes
  useEffect(() => {
    const handleResize = () => {
      const mobile = window.innerWidth < 1024; // lg breakpoint
      setIsMobile(mobile);
      setIsOpen(!mobile); // Default: closed on mobile, open on desktop
    };

    handleResize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // Reset sidebar on route change
  useEffect(() => {
    if (isMobile) {
      setIsOpen(false);
      setIsStatusMenuOpen(false);
    }
  }, [location.pathname, isMobile]);

  // Persist collapse state to localStorage
  useEffect(() => {
    localStorage.setItem("sidebarCollapsed", JSON.stringify(isCollapsed));
  }, [isCollapsed]);

  const toggleSidebar = () => setIsOpen(!isOpen);
  const toggleCollapse = () => setIsCollapsed(!isCollapsed);

  const openProfilePage = () => {
    navigate("/settings");
    if (isMobile) {
      setIsOpen(false);
    }
  };

  // For desktop: collapsed width is icon-only (approx 80px)
  // For desktop: expanded width is full 256px
  const sidebarWidth = !isMobile && isCollapsed ? "w-20" : "w-64";
  const minWidth = !isMobile && isCollapsed ? "min-w-20" : "min-w-64";

  return (
    <>
      {/* Mobile toggle button */}
      {isMobile && (
        <button
          onClick={toggleSidebar}
          className="fixed top-4 left-4 z-40 p-2 rounded-lg bg-blue-500 text-white hover:bg-blue-600"
          aria-label="Toggle sidebar"
        >
          {isOpen ? <X size={24} /> : <Menu size={24} />}
        </button>
      )}

      {/* Mobile overlay */}
      {isMobile && isOpen && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 z-30"
          onClick={() => {
            setIsOpen(false);
            setIsStatusMenuOpen(false);
          }}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`
          ${isMobile ? "fixed left-0 top-0 h-screen z-40" : `${sidebarWidth} ${minWidth}`}
          bg-white border-r flex flex-col
          transition-all duration-300 ease-in-out
          ${!isOpen && isMobile ? "-translate-x-full" : "translate-x-0"}
        `}
      >
        {/* Top: Collapse button (desktop only) + User Profile / Avatar (mobile) */}
        {!isMobile && (
          <div className="flex items-center justify-between p-4 border-b">
            {!isCollapsed && <span className="text-sm font-semibold">Menu</span>}
            <button
              onClick={toggleCollapse}
              className="p-1 rounded-lg hover:bg-gray-200 transition"
              aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            >
              {isCollapsed ? <ChevronRight size={20} /> : <ChevronLeft size={20} />}
            </button>
          </div>
        )}

        {isMobile && (
          <div className="p-4 border-b bg-gray-50">
            {currentUser ? (
              <div className="flex items-center gap-3">
                <button
                  onClick={openProfilePage}
                  className="relative rounded-full"
                  aria-label="Open profile"
                >
                  <UserAvatar
                    userId={currentUser.accountId}
                    avatar={currentUser.avatarUrl}
                    size={40}
                    status={effectiveStatus}
                  />
                </button>
                <div className="flex-1 min-w-0">
                  <button
                    onClick={openProfilePage}
                    className="font-semibold text-sm truncate text-left hover:underline"
                    title="Open profile"
                  >
                    {currentUser.displayName || currentUser.username || "User"}
                  </button>
                  <p className="text-xs text-gray-500 truncate">
                    @{currentUser.username}
                  </p>
                </div>
                <button
                  onClick={() => setIsStatusMenuOpen((prev) => !prev)}
                  className="rounded-md border border-gray-200 px-2 py-1 text-xs text-gray-700 hover:bg-gray-100"
                >
                  Status
                </button>
              </div>
            ) : (
              <div className="text-sm text-gray-500">Loading...</div>
            )}

            {isStatusMenuOpen && (
              <div className="mt-3 rounded-lg border border-gray-200 bg-white p-2 shadow-sm">
                {statusOptions.map((status) => (
                  <button
                    key={status}
                    onClick={() => void handleStatusChange(status)}
                    disabled={statusSaving}
                    className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm text-left hover:bg-gray-100 disabled:opacity-60"
                  >
                    <span className={`h-2.5 w-2.5 rounded-full ${getStatusDotClass(status)}`} />
                    {status}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Desktop User Profile Section */}
        {!isMobile && !isCollapsed && (
          <div className="p-4 border-b bg-gray-50">
            {currentUser ? (
              <div>
                <div className="flex items-center gap-3">
                  <button
                    onClick={openProfilePage}
                    className="relative rounded-full"
                    aria-label="Open profile"
                  >
                    <UserAvatar
                      userId={currentUser.accountId}
                      avatar={currentUser.avatarUrl}
                      size={40}
                      status={effectiveStatus}
                    />
                  </button>
                  <div className="flex-1 min-w-0">
                    <button
                      onClick={openProfilePage}
                      className="font-semibold text-sm truncate text-left hover:underline"
                      title="Open profile"
                    >
                      {currentUser.displayName || currentUser.username || "User"}
                    </button>
                    <p className="text-xs text-gray-500 truncate">
                      @{currentUser.username}
                    </p>
                  </div>
                  <button
                    onClick={() => setIsStatusMenuOpen((prev) => !prev)}
                    className="rounded-md border border-gray-200 px-2 py-1 text-xs text-gray-700 hover:bg-gray-100"
                  >
                    Status
                  </button>
                </div>

                {isStatusMenuOpen && (
                  <div className="mt-3 rounded-lg border border-gray-200 bg-white p-2 shadow-sm">
                    {statusOptions.map((status) => (
                      <button
                        key={status}
                        onClick={() => void handleStatusChange(status)}
                        disabled={statusSaving}
                        className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm text-left hover:bg-gray-100 disabled:opacity-60"
                      >
                        <span className={`h-2.5 w-2.5 rounded-full ${getStatusDotClass(status)}`} />
                        {status}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <div className="text-sm text-gray-500">Loading...</div>
            )}
          </div>
        )}

        {/* Desktop collapsed avatar only */}
        {!isMobile && isCollapsed && (
          <div className="p-2 border-b bg-gray-50 flex justify-center">
            {currentUser ? (
              <button
                onClick={openProfilePage}
                className="relative rounded-full"
                aria-label="Open profile"
              >
                  <UserAvatar
                    userId={currentUser.accountId}
                    avatar={currentUser.avatarUrl}
                    size={40}
                    status={effectiveStatus}
                  />
              </button>
            ) : (
              <div className="w-10 h-10 bg-gray-300 rounded-full" />
            )}
          </div>
        )}

        <div className={`relative z-[110] border-b bg-white ${isCollapsed && !isMobile ? "p-2" : "p-3"}`}>
          <NotificationBell compact={isCollapsed && !isMobile} />
        </div>

        {/* Middle: Navigation Items */}
        <nav className="flex-1 overflow-y-auto p-2 space-y-2">
          {menu.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => isMobile && setIsOpen(false)}
              className={({ isActive }) =>
                `block px-4 py-2 rounded-lg transition text-center ${isCollapsed ? "px-2" : "text-left"} ${
                  isActive
                    ? "bg-blue-500 text-white"
                    : "text-gray-700 hover:bg-gray-200"
                }`
              }
              title={isCollapsed ? item.label : undefined}
            >
              <span className="relative flex items-center justify-center gap-2">
                <span>{isCollapsed ? item.label[0].toUpperCase() : item.label}</span>
                {item.to === "/friends" && unreadFriendRequests > 0 && !isCollapsed && (
                  <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                    {unreadFriendRequests > 99 ? "99+" : unreadFriendRequests}
                  </span>
                )}
                {item.to === "/friends" && unreadFriendRequests > 0 && isCollapsed && (
                  <span className="absolute -right-2 -top-2 inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                    {unreadFriendRequests > 99 ? "99+" : unreadFriendRequests}
                  </span>
                )}
              </span>
            </NavLink>
          ))}
        </nav>

        {/* Bottom: Logout */}
        <div className="border-t p-2 bg-gray-50">
          <button
            onClick={logout}
            className={`w-full px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600 transition ${
              isCollapsed ? "text-xs px-1 py-1" : ""
            }`}
            title={isCollapsed ? "Logout" : undefined}
          >
            {isCollapsed ? "L" : "Logout"}
          </button>
        </div>
      </aside>
    </>
  );
};

export default Sidebar;
