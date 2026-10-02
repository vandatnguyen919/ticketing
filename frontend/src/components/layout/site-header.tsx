import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LogOut, Menu, Ticket, UserRound, X } from 'lucide-react';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger
} from '@/components/ui/sheet';

type SiteUser = {
  email: string;
  name: string;
};

type SiteHeaderProps = {
  user?: SiteUser | null;
  onLogout?: () => void;
};

const navigationLinks = [
  { label: 'Home', to: '/' },
  { label: 'Events', to: '/#events' },
  { label: 'About', to: '/#about' }
];

function initials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

export function SiteHeader({ user, onLogout }: SiteHeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const currentPath = `${location.pathname}${location.hash}`;

  return (
    <header className="sticky top-0 z-40 border-b border-border/70 bg-background/85 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Link
          aria-label="Ticketing home"
          className="flex shrink-0 items-center gap-2 font-semibold tracking-tight"
          to="/"
        >
          <span className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Ticket aria-hidden="true" className="size-5" />
          </span>
          <span>Ticketing</span>
        </Link>

        <nav aria-label="Main navigation" className="hidden items-center gap-1 md:flex">
          {navigationLinks.map((item) => (
            <Link
              aria-current={currentPath === item.to ? 'page' : undefined}
              className="rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
              key={item.label}
              to={item.to}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="Open account menu"
              className="rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
            >
              <Avatar>
                <AvatarFallback>
                  {user ? initials(user.name) : <UserRound aria-hidden="true" />}
                </AvatarFallback>
              </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="min-w-48">
              <DropdownMenuGroup>
                <DropdownMenuLabel>
                  <span className="block truncate">{user?.name ?? 'Your account'}</span>
                  {user ? (
                    <span className="block truncate text-xs font-normal text-muted-foreground">
                      {user.email}
                    </span>
                  ) : null}
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                {user ? (
                  <>
                    <DropdownMenuItem disabled>Profile</DropdownMenuItem>
                    <DropdownMenuItem disabled>Settings</DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      className="text-destructive focus:text-destructive"
                      onClick={onLogout}
                    >
                      <LogOut aria-hidden="true" data-icon="inline-start" />
                      Log out
                    </DropdownMenuItem>
                  </>
                ) : (
                  <>
                    <DropdownMenuItem render={<Link to="/login" />}>
                      Log in
                    </DropdownMenuItem>
                    <DropdownMenuItem render={<Link to="/signup" />}>
                      Create account
                    </DropdownMenuItem>
                  </>
                )}
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>

          <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
            <SheetTrigger
              aria-label="Open navigation menu"
              className="inline-flex size-9 items-center justify-center rounded-md text-foreground outline-none transition-colors hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring md:hidden"
            >
              <Menu aria-hidden="true" className="size-5" />
            </SheetTrigger>
            <SheetContent
              side="right"
              className="w-[min(20rem,85vw)]"
              showCloseButton={false}
            >
              <SheetClose
                aria-label="Close navigation menu"
                className="absolute right-3 top-3 inline-flex size-8 items-center justify-center rounded-md text-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <X aria-hidden="true" className="size-4" />
              </SheetClose>
              <SheetHeader>
                <SheetTitle className="sr-only">Main navigation</SheetTitle>
              </SheetHeader>
              <nav aria-label="Mobile navigation" className="flex flex-col gap-1 px-4">
                {navigationLinks.map((item) => (
                  <Link
                    aria-current={currentPath === item.to ? 'page' : undefined}
                    className="rounded-md px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent"
                    key={item.label}
                    onClick={() => setMenuOpen(false)}
                    to={item.to}
                  >
                    {item.label}
                  </Link>
                ))}
                {!user ? (
                  <>
                    <Link
                      className="rounded-md px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent"
                      onClick={() => setMenuOpen(false)}
                      to="/login"
                    >
                      Log in
                    </Link>
                    <Link
                      className="rounded-md px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent"
                      onClick={() => setMenuOpen(false)}
                      to="/signup"
                    >
                      Create account
                    </Link>
                  </>
                ) : (
                  <button
                    className="rounded-md px-3 py-2.5 text-left text-sm font-medium text-foreground transition-colors hover:bg-accent"
                    onClick={() => {
                      setMenuOpen(false);
                      onLogout?.();
                    }}
                    type="button"
                  >
                    Log out
                  </button>
                )}
              </nav>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
}
