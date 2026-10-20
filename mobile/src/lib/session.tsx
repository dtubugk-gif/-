import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, loadSession, saveToken, setUnauthorizedHandler, type Me } from "./api";
import { getLocale, setLocale } from "./i18n";

interface Session {
  ready: boolean;
  signedIn: boolean;
  me: Me | null;
  refresh: () => Promise<Me | null>;
  signIn: (token: string, me: Me) => Promise<void>;
  signOut: () => Promise<void>;
  setMe: (me: Me) => void;
}

const Ctx = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [me, setMeState] = useState<Me | null>(null);

  const setMe = useCallback((m: Me) => {
    setMeState(m);
    if (m.locale !== getLocale()) setLocale(m.locale);
  }, []);

  const signOut = useCallback(async () => {
    await saveToken(null);
    setToken(null);
    setMeState(null);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const m = await api<Me>("/v1/me");
      setMe(m);
      return m;
    } catch {
      return null;
    }
  }, [setMe]);

  useEffect(() => {
    setUnauthorizedHandler(() => void signOut());
    (async () => {
      const t = await loadSession();
      setToken(t);
      setReady(true);
      if (t) void refresh();
    })();
  }, [refresh, signOut]);

  const signIn = useCallback(
    async (t: string, m: Me) => {
      await saveToken(t);
      setToken(t);
      setMe(m);
    },
    [setMe],
  );

  const value = useMemo(
    () => ({ ready, signedIn: !!token, me, refresh, signIn, signOut, setMe }),
    [ready, token, me, refresh, signIn, signOut, setMe],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession(): Session {
  const s = useContext(Ctx);
  if (!s) throw new Error("useSession outside SessionProvider");
  return s;
}
