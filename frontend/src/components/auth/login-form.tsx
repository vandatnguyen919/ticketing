import { useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from '@/components/ui/card';
import {
  Field,
  FieldGroup,
  FieldLabel
} from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Link } from 'react-router-dom';
import { OAuthButtons, type AuthFormProps } from './auth-form';

export function LoginForm({ onSignIn }: AuthFormProps) {
  const [notice, setNotice] = useState<string | null>(null);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setNotice(
      'Email and password sign-in is not available yet. Continue with Google or GitHub.'
    );
  };

  return (
    <Card className="w-full rounded-2xl py-5 shadow-sm">
      <CardHeader className="px-6">
        <CardTitle>Login to your account</CardTitle>
        <CardDescription>
          Enter your email below to login to your account
        </CardDescription>
      </CardHeader>
      <CardContent className="px-6">
        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="login-email">Email</FieldLabel>
              <Input
                autoComplete="email"
                id="login-email"
                name="email"
                placeholder="m@example.com"
                required
                type="email"
                className="h-9"
              />
            </Field>
            <Field>
              <div className="flex items-center justify-between gap-2">
                <FieldLabel htmlFor="login-password">Password</FieldLabel>
                <a
                  className="text-sm underline-offset-4 hover:underline"
                  href="#forgot-password"
                  onClick={(event) => {
                    event.preventDefault();
                    setNotice(
                      'Password reset is not available yet. Continue with Google or GitHub.'
                    );
                  }}
                >
                  Forgot your password?
                </a>
              </div>
              <Input
                autoComplete="current-password"
                id="login-password"
                name="password"
                required
                type="password"
                className="h-9"
              />
            </Field>
          </FieldGroup>
          <div className="flex flex-col gap-3">
            <Button className="h-9 w-full" type="submit">
              Login
            </Button>
            <OAuthButtons action="Login" onSignIn={onSignIn} />
          </div>
          {notice ? (
            <p className="text-sm text-muted-foreground" role="status">
              {notice}
            </p>
          ) : null}
        </form>
      </CardContent>
      <CardFooter className="justify-center border-t-0 bg-transparent px-6 pt-0">
        <p className="text-sm text-muted-foreground">
          Don&apos;t have an account?{' '}
          <Link
            className="text-foreground underline underline-offset-4"
            to="/signup"
          >
            Sign up
          </Link>
        </p>
      </CardFooter>
    </Card>
  );
}
