import {useAuth} from "../../hooks/useAuth";

const Header = () => {
  const { logout } = useAuth();

  return (
    <header className="h-16 bg-white border-b flex items-center justify-between px-6">
      <h1 className="font-semibold text-lg">Welcome 👋</h1>

      <button
        onClick={logout}
        className="px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600"
      >
        Logout
      </button>
    </header>
  );
};

export default Header;
