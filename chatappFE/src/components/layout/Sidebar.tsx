import { NavLink, useLocation } from "react-router-dom";
import { useState, useEffect } from "react";
import { Menu, X, ChevronLeft, ChevronRight } from "lucide-react";
import { useAuth } from "../../hooks/useAuth";
import UserAvatar from "../user/UserAvatar";
import NotificationBell from "../notifications/NotificationBell";

const menu = [
  { to: "/chat", label: "Chat" },
  { to: "/friends", label: "Friends" },
  { to: "/notifications", label: "Notifications" },
  { to: "/me", label: "Profile" },
];

const Sidebar = () => {
  const { logout, currentUser } = useAuth();
  const location = useLocation();
  
  // Responsive sidebar state
  const [isOpen, setIsOpen] = useState(true);
  const [isMobile, setIsMobile] = useState(false);
  
  // Desktop collapse state (persists across navigation)
  const [isCollapsed, setIsCollapsed] = useState(() => {
    const saved = localStorage.getItem("sidebarCollapsed");
    return saved ? JSON.parse(saved) : false;
  });

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
    }
  }, [location.pathname, isMobile]);

  // Persist collapse state to localStorage
  useEffect(() => {
    localStorage.setItem("sidebarCollapsed", JSON.stringify(isCollapsed));
  }, [isCollapsed]);

  const toggleSidebar = () => setIsOpen(!isOpen);
  const toggleCollapse = () => setIsCollapsed(!isCollapsed);

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
          onClick={() => setIsOpen(false)}
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
                <UserAvatar
                  userId={currentUser.accountId}
                  avatar={currentUser.avatarUrl}
                  size={40}
                />
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-sm truncate">
                    {currentUser.displayName || currentUser.username || "User"}
                  </p>
                  <p className="text-xs text-gray-500 truncate">
                    @{currentUser.username}
                  </p>
                </div>
              </div>
            ) : (
              <div className="text-sm text-gray-500">Loading...</div>
            )}
          </div>
        )}

        {/* Desktop User Profile Section */}
        {!isMobile && !isCollapsed && (
          <div className="p-4 border-b bg-gray-50">
            {currentUser ? (
              <div className="flex items-center gap-3">
                <UserAvatar
                  userId={currentUser.accountId}
                  avatar={currentUser.avatarUrl}
                  size={40}
                />
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-sm truncate">
                    {currentUser.displayName || currentUser.username || "User"}
                  </p>
                  <p className="text-xs text-gray-500 truncate">
                    @{currentUser.username}
                  </p>
                </div>
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
              <UserAvatar
                userId={currentUser.accountId}
                avatar={currentUser.avatarUrl}
                size={40}
              />
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
              {isCollapsed ? item.label[0].toUpperCase() : item.label}
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
