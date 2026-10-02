import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { SiteFooter } from './site-footer';
import { SiteHeader } from './site-header';

type SiteUser = {
  email: string;
  name: string;
};

type SiteLayoutProps = {
  children: ReactNode;
  user?: SiteUser | null;
  onLogout?: () => void;
};

export function SiteLayout({ children, user, onLogout }: SiteLayoutProps) {
  const location = useLocation();

  useEffect(() => {
    if (location.hash) {
      document.getElementById(location.hash.slice(1))?.scrollIntoView();
      return;
    }

    window.scrollTo(0, 0);
  }, [location.hash, location.pathname]);

  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground">
      <SiteHeader user={user} onLogout={onLogout} />
      <div className="flex-1">{children}</div>
      <SiteFooter />
    </div>
  );
}
