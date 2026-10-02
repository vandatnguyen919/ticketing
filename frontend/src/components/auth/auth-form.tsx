import { Button } from '@/components/ui/button';
import { GitBranch } from 'lucide-react';

export type AuthProvider = 'github' | 'google';

export type AuthFormProps = {
  onSignIn: (provider: AuthProvider) => void;
};

export function OAuthButtons({
  action,
  onSignIn
}: AuthFormProps & { action: 'Login' | 'Sign up' }) {
  return (
    <div className="flex flex-col gap-3">
      <Button
        className="h-9 w-full"
        variant="outline"
        type="button"
        onClick={() => onSignIn('google')}
      >
        {action} with Google
      </Button>
      <Button
        className="h-9 w-full"
        variant="outline"
        type="button"
        onClick={() => onSignIn('github')}
      >
        <GitBranch aria-hidden="true" data-icon="inline-start" />
        {action} with GitHub
      </Button>
    </div>
  );
}
