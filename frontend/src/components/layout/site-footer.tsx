import { AtSign, GitBranch, MessageCircle } from 'lucide-react';
import { Link } from 'react-router-dom';

const sitemapLinks = [
  { label: 'Events', to: '/#events' },
  { label: 'About', to: '/#about' },
  { label: 'Log in', to: '/login' },
  { label: 'Create account', to: '/signup' }
];

const socialLinks = [
  { label: 'GitHub', href: 'https://github.com', icon: GitBranch },
  { label: 'X', href: 'https://x.com', icon: AtSign },
  { label: 'Discord', href: 'https://discord.com', icon: MessageCircle }
];

export function SiteFooter() {
  return (
    <footer className="border-t bg-muted/30">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-10 sm:px-6 md:grid-cols-[1.5fr_1fr_1fr]">
        <div id="about" className="max-w-sm">
          <Link className="font-semibold tracking-tight text-foreground" to="/">
            Ticketing
          </Link>
          <p className="mt-3 text-sm leading-6 text-muted-foreground">
            Find upcoming events and reserve a ticket for your next experience.
          </p>
        </div>

        <nav aria-label="Footer navigation">
          <h2 className="text-sm font-semibold">Explore</h2>
          <ul className="mt-3 flex flex-col gap-2">
            {sitemapLinks.map((item) => (
              <li key={item.label}>
                <Link
                  className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                  to={item.to}
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav aria-label="Social media">
          <h2 className="text-sm font-semibold">Follow us</h2>
          <ul className="mt-3 flex flex-col gap-2">
            {socialLinks.map(({ label, href, icon: Icon }) => (
              <li key={label}>
                <a
                  aria-label={label}
                  className="inline-flex items-center gap-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
                  href={href}
                  rel="noreferrer"
                  target="_blank"
                >
                  <Icon aria-hidden="true" className="size-4" />
                  {label}
                </a>
              </li>
            ))}
          </ul>
        </nav>
      </div>

      <div className="border-t">
        <p className="mx-auto max-w-6xl px-4 py-5 text-sm text-muted-foreground sm:px-6">
          © {new Date().getFullYear()} Ticketing. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
