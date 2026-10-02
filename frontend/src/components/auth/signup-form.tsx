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
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel
} from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Link } from 'react-router-dom';
import { OAuthButtons, type AuthFormProps } from './auth-form';

export function SignupForm({ onSignIn }: AuthFormProps) {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (password !== confirmPassword) {
      setPasswordError('Passwords do not match.');
      setNotice(null);
      return;
    }

    setPasswordError(null);
    setNotice(
      'Email and password account creation is not available yet. Continue with Google or GitHub.'
    );
  };

  return (
    <Card className="w-full rounded-2xl py-5 shadow-sm">
      <CardHeader className="px-6">
        <CardTitle>Create an account</CardTitle>
        <CardDescription>
          Enter your information below to create your account
        </CardDescription>
      </CardHeader>
      <CardContent className="px-6">
        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="gap-5">
            <Field>
              <FieldLabel htmlFor="signup-name">Full Name</FieldLabel>
              <Input
                autoComplete="name"
                id="signup-name"
                name="name"
                placeholder="John Doe"
                required
                className="h-9"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="signup-email">Email</FieldLabel>
              <Input
                autoComplete="email"
                id="signup-email"
                name="email"
                placeholder="m@example.com"
                required
                type="email"
                className="h-9"
              />
              <FieldDescription>
                We&apos;ll use this to contact you. We will not share your email
                with anyone else.
              </FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="signup-password">Password</FieldLabel>
              <Input
                autoComplete="new-password"
                id="signup-password"
                minLength={8}
                name="password"
                onChange={(event) => {
                  const value = event.target.value;
                  setPassword(value);
                  setPasswordError(
                    confirmPassword && value !== confirmPassword
                      ? 'Passwords do not match.'
                      : null
                  );
                }}
                required
                type="password"
                value={password}
                className="h-9"
              />
              <FieldDescription>Must be at least 8 characters long.</FieldDescription>
            </Field>
            <Field data-invalid={Boolean(passwordError)}>
              <FieldLabel htmlFor="signup-confirm-password">
                Confirm Password
              </FieldLabel>
              <Input
                aria-invalid={Boolean(passwordError)}
                autoComplete="new-password"
                id="signup-confirm-password"
                name="confirmPassword"
                onChange={(event) => {
                  const value = event.target.value;
                  setConfirmPassword(value);
                  setPasswordError(
                    value && value !== password
                      ? 'Passwords do not match.'
                      : null
                  );
                }}
                required
                type="password"
                value={confirmPassword}
                className="h-9"
              />
              <FieldDescription>Please confirm your password.</FieldDescription>
              {passwordError ? <FieldError>{passwordError}</FieldError> : null}
            </Field>
          </FieldGroup>
          <div className="flex flex-col gap-3">
            <Button className="h-9 w-full" type="submit">
              Create Account
            </Button>
            <OAuthButtons action="Sign up" onSignIn={onSignIn} />
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
          Already have an account?{' '}
          <Link
            className="text-foreground underline underline-offset-4"
            to="/login"
          >
            Sign in
          </Link>
        </p>
      </CardFooter>
    </Card>
  );
}
