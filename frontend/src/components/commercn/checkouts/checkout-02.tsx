import { ArrowLeft, CalendarDays, Ticket } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export type CheckoutEvent = {
  id: number;
  title: string;
  eventDate: string;
  remainingTickets: number;
};

type EventCheckoutProps = {
  event: CheckoutEvent;
  authenticated: boolean;
  isBooking: boolean;
  onBack: () => void;
  onReserve: () => void;
};

export function EventCheckout({
  event,
  authenticated,
  isBooking,
  onBack,
  onReserve,
}: EventCheckoutProps) {
  const soldOut = event.remainingTickets <= 0;

  return (
    <section aria-labelledby="checkout-title" className="mb-8">
      <Card>
        <CardHeader>
          <CardTitle id="checkout-title" className="text-xl">
            Review your ticket
          </CardTitle>
          <CardDescription>
            Confirm the event before reserving your ticket.
          </CardDescription>
        </CardHeader>

        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div>
            <p className="text-sm text-muted-foreground">Event</p>
            <p className="mt-1 font-semibold">{event.title}</p>
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Date</p>
            <p className="mt-1 flex items-center gap-2">
              <CalendarDays className="size-4 shrink-0" aria-hidden="true" />
              {new Date(event.eventDate).toLocaleString()}
            </p>
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Ticket</p>
            <p className="mt-1 flex items-center gap-2">
              <Ticket className="size-4 shrink-0" aria-hidden="true" />
              1 ticket
            </p>
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Availability</p>
            <p className="mt-1">
              {soldOut ? "Sold out" : `${event.remainingTickets} remaining`}
            </p>
          </div>
          <p className="text-sm text-muted-foreground sm:col-span-2">
            No payment is collected. This reservation books one ticket.
          </p>
        </CardContent>

        <CardFooter className="justify-between gap-3">
          <Button variant="outline" onClick={onBack}>
            <ArrowLeft data-icon="inline-start" aria-hidden="true" />
            Back to events
          </Button>
          <Button
            disabled={!authenticated || soldOut || isBooking}
            onClick={onReserve}
          >
            {isBooking
              ? "Reserving..."
              : soldOut
                ? "Sold out"
                : authenticated
                  ? "Reserve ticket"
                  : "Sign in to reserve"}
          </Button>
        </CardFooter>
      </Card>
    </section>
  );
}
