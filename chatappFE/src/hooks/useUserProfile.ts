import { useEffect, useState } from "react";
import { useUserStore } from "../store/user.store";

export const useUserProfile = (userId?: string) => {
  const user = useUserStore((s) =>
    userId ? s.users[userId] : undefined
  );
  const fetchUsers = useUserStore((s) => s.fetchUsers);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userId || user) return;

    let cancelled = false;

    const load = async () => {
      try {
        setLoading(true);
        setError(null);

        await fetchUsers([userId]);
      } catch {
        if (!cancelled) setError("Failed to load user");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();

    return () => {
      cancelled = true;
    };
  }, [userId, user, fetchUsers]);

  return { user, loading, error };
};