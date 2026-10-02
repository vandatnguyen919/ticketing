import { LoginForm } from './login-form';
import { SignupForm } from './signup-form';
import type { AuthFormProps } from './auth-form';
import { SiteLayout } from '@/components/layout/site-layout';

type AuthPageProps = AuthFormProps & {
  mode: 'login' | 'signup';
};

export function AuthPage({ mode, onSignIn }: AuthPageProps) {
  return (
    <SiteLayout>
      <main className="grid min-h-[32rem] flex-1 place-items-center px-4 py-10">
        <section
          aria-label={mode === 'login' ? 'Log in' : 'Create an account'}
          className="w-full max-w-sm"
        >
          {mode === 'login' ? (
            <LoginForm onSignIn={onSignIn} />
          ) : (
            <SignupForm onSignIn={onSignIn} />
          )}
        </section>
      </main>
    </SiteLayout>
  );
}
