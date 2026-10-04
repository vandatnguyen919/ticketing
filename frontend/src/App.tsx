import { useEffect, useRef, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { CalendarDays } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { AuthPage } from '@/components/auth/auth-page';
import { SiteLayout } from '@/components/layout/site-layout';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from '@/components/ui/card';
import {
  EventCheckout,
  type CheckoutEvent,
} from '@/components/commercn/checkouts/checkout-02';
import { API_ORIGIN, apiErrorMessage, apiRequest, UnauthorizedApiError } from '@/lib/api';
import { exchangeOAuthSession, loadCurrentUser, logout as logoutSession, type UserProfile } from '@/lib/auth';

type EventItem = CheckoutEvent;

const startSignIn = (provider: 'github' | 'google') => {
  window.location.assign(`${API_ORIGIN}/oauth2/authorization/${provider}`);
};

function EventCatalog() {
  const location = useLocation();
  const navigate = useNavigate();
  const [events, setEvents] = useState<EventItem[]>([]);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [bookingLoading, setBookingLoading] = useState<number | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<EventItem | null>(null);
  const [error, setError] = useState<string | null>(null);
  const oauthCallbackStarted = useRef(false);

  const fetchEvents = async () => {
    setLoading(true);
    try {
      const response = await apiRequest<EventItem[]>('/events');
      setEvents(response.data);
    } catch (err) {
      setError(apiErrorMessage(err, 'Unable to load events.'));
    } finally {
      setLoading(false);
    }
  };

  const logout = async () => {
    setError(null);
    try {
      await logoutSession();
      setUser(null);
    } catch (err) {
      if (err instanceof UnauthorizedApiError) {
        setUser(null);
        return;
      }
      setError(apiErrorMessage(err, 'Unable to log out.'));
    }
  };

  const bookTicket = async (eventId: number) => {
    if (!user) {
      setError('Please sign in before booking a ticket.');
      return;
    }

    setBookingLoading(eventId);
    setError(null);

    try {
      await apiRequest(`/events/${eventId}/book`, { method: 'POST' });

      await fetchEvents();
      setSelectedEvent(null);
    } catch (err) {
      if (err instanceof UnauthorizedApiError) {
        setUser(null);
      }
      setError(apiErrorMessage(err, 'Booking failed.'));
    } finally {
      setBookingLoading(null);
    }
  };

  useEffect(() => {
    void fetchEvents();
  }, []);

  useEffect(() => {
    if (location.pathname !== '/auth/callback') {
      oauthCallbackStarted.current = false;
      return;
    }
    if (oauthCallbackStarted.current) {
      return;
    }

    oauthCallbackStarted.current = true;
    const query = new URLSearchParams(location.search);
    if (query.has('error')) {
      setUser(null);
      navigate('/', { replace: true });
      setError('Sign-in failed. Please try again.');
      return;
    }

    const finishSignIn = async () => {
      try {
        setUser(await exchangeOAuthSession());
        setError(null);
      } catch (err) {
        setUser(null);
        setError(apiErrorMessage(err, 'Unable to complete sign-in.'));
      } finally {
        navigate('/', { replace: true });
      }
    };

    void finishSignIn();
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    if (location.pathname === '/auth/callback') {
      return;
    }

    let active = true;
    const loadProfile = async () => {
      try {
        const profile = await loadCurrentUser();
        if (active) {
          setUser(profile);
        }
      } catch (err) {
        if (!active) {
          return;
        }
        if (err instanceof UnauthorizedApiError) {
          setUser(null);
        } else {
          setError(apiErrorMessage(err, 'Unable to load your profile.'));
        }
      }
    };

    void loadProfile();
    return () => {
      active = false;
    };
  }, [location.pathname]);

  return (
    <SiteLayout user={user} onLogout={logout}>
      <main className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 sm:py-12">
        <section id="events" aria-labelledby="events-heading" className="space-y-6">
          <div>
            <h2 id="events-heading" className="text-3xl font-semibold tracking-tight">
              Upcoming events
            </h2>
            <p className="mt-2 text-muted-foreground">
              Find your next experience and reserve a ticket.
            </p>
          </div>

          {error ? (
            <Card role="alert" className="border-destructive/50 bg-destructive/10 text-destructive">
              <CardContent className="pt-4">{error}</CardContent>
            </Card>
          ) : null}

          {selectedEvent ? (
            <EventCheckout
              event={selectedEvent}
              authenticated={Boolean(user)}
              isBooking={bookingLoading === selectedEvent.id}
              onBack={() => setSelectedEvent(null)}
              onReserve={() => bookTicket(selectedEvent.id)}
            />
          ) : null}

          {loading ? (
            <Card>
              <CardContent className="py-12 text-center text-muted-foreground">
                Loading upcoming events...
              </CardContent>
            </Card>
          ) : events.length === 0 ? (
            <Card>
              <CardHeader>
                <CardTitle>No events available</CardTitle>
                <CardDescription>
                  Check back later for upcoming events.
                </CardDescription>
              </CardHeader>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {events.map((event) => {
                const soldOut = event.remainingTickets <= 0;
                return (
                  <Card key={event.id} className="h-full">
                    <CardHeader className="flex-1">
                      <div className="flex items-start justify-between gap-3">
                        <div className="space-y-1">
                          <CardDescription>Event</CardDescription>
                          <CardTitle className="text-lg">{event.title}</CardTitle>
                        </div>
                        <Badge variant={soldOut ? 'destructive' : 'secondary'}>
                          {soldOut ? 'Sold out' : `${event.remainingTickets} left`}
                        </Badge>
                      </div>
                    </CardHeader>
                    <CardContent>
                      <div className="flex items-start gap-2 text-sm text-muted-foreground">
                        <CalendarDays
                          className="mt-0.5 size-4 shrink-0"
                          aria-hidden="true"
                        />
                        <time dateTime={event.eventDate}>
                          {new Date(event.eventDate).toLocaleString()}
                        </time>
                      </div>
                    </CardContent>
                    <CardFooter>
                      <Button
                        className="w-full"
                        variant="outline"
                        onClick={() => {
                          if (!user) {
                            navigate('/login');
                            return;
                          }
                          setError(null);
                          setSelectedEvent(event);
                        }}
                        disabled={soldOut || bookingLoading === event.id}
                      >
                        {bookingLoading === event.id
                          ? 'Booking...'
                          : soldOut
                            ? 'Sold out'
                            : !user
                              ? 'Sign in to book'
                              : 'Review ticket'}
                      </Button>
                    </CardFooter>
                  </Card>
                );
              })}
            </div>
          )}
        </section>
      </main>
    </SiteLayout>
  );
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<EventCatalog />} />
      <Route path="/auth/callback" element={<EventCatalog />} />
      <Route
        path="/login"
        element={<AuthPage mode="login" onSignIn={startSignIn} />}
      />
      <Route
        path="/signup"
        element={<AuthPage mode="signup" onSignIn={startSignIn} />}
      />
      <Route path="*" element={<Navigate replace to="/" />} />
    </Routes>
  );
}

export default App;
