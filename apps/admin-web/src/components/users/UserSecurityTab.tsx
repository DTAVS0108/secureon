"use client";

import { Button } from "@/components/ui/Button";
import { SecuritySection } from "@/components/users/SecuritySection";
import { UserAccountSecurityActions } from "@/components/users/UserAccountSecurityActions";
import { UserEmailActions } from "@/components/users/UserEmailActions";
import { UserPasswordActions } from "@/components/users/UserPasswordActions";
import { UserSessionsCard } from "@/components/users/UserSessionsCard";
import { RoleEndDatesCard, UserAttributesCard, UserIdentitiesCard } from "@/components/users/UserProfileExtras";
import type { User } from "@/lib/types";

export function UserSecurityTab({
  user,
  applicationId,
  mfaFactorCount = 0,
  showMfaLink = false,
  onChanged,
  onOpenMfaTab,
  onOpenSignInsTab,
}: {
  user: User;
  applicationId?: string;
  mfaFactorCount?: number;
  showMfaLink?: boolean;
  onChanged?: () => void | Promise<void>;
  onOpenMfaTab?: () => void;
  onOpenSignInsTab?: () => void;
}) {
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4">
      <SecuritySection
        title="Account access"
        description="Status, lockout, and suspension. Suspended users cannot sign in."
      >
        <UserAccountSecurityActions user={user} onChanged={onChanged} />
      </SecuritySection>

      <SecuritySection
        title="Sessions"
        description={
          applicationId
            ? "Where the user is signed in to this application. Signing out ends its refresh tokens and tells the application (back-channel logout)."
            : "Browser sessions and the applications the user is signed in to. Signing out ends them all and tells the applications."
        }
      >
        <UserSessionsCard userId={user.id} applicationId={applicationId} />
      </SecuritySection>

      {applicationId && (
        <SecuritySection
          title="Role end dates"
          description="A role can be given until a date: it stops counting then (tokens, console access) and is removed."
        >
          <RoleEndDatesCard userId={user.id} applicationId={applicationId} />
        </SecuritySection>
      )}

      <SecuritySection
        title="Profile attributes"
        description="Values from sign-up fields, imports or set here. They can go in tokens (Integration › Token claims)."
      >
        <UserAttributesCard userId={user.id} applicationId={applicationId} />
      </SecuritySection>

      <SecuritySection title="Linked sign-in" description="Google, SAML and other accounts this user signs in with.">
        <UserIdentitiesCard userId={user.id} applicationId={applicationId} />
      </SecuritySection>

      <SecuritySection
        title="Email verification"
        description="Unverified users cannot use password sign-in. They can confirm via inbox link or you can verify manually in Edit user."
      >
        <UserEmailActions
          userId={user.id}
          userEmail={user.email}
          emailVerified={user.emailVerified}
          applicationId={applicationId}
          onChanged={onChanged}
        />
      </SecuritySection>

      <SecuritySection
        title="Password & credentials"
        description="Email links for self-service password setup, or set/remove the password directly as admin."
      >
        <UserPasswordActions
          userId={user.id}
          userEmail={user.email}
          hasPassword={user.hasPassword ?? false}
          applicationId={applicationId}
          onChanged={onChanged}
        />
      </SecuritySection>

      {/* showMfaLink false: two-factor is off for this application, so only the sign-in history remains. */}
      <SecuritySection
        title={showMfaLink ? "Two Factor Authentication" : "Sign-in history"}
        description={
          !showMfaLink
            ? "Recent sign-ins of this user to this application."
            : mfaFactorCount > 0
              ? `${mfaFactorCount} option(s) set up. Manage them on the Two-step tab.`
              : "No two-factor options set up for this user yet."
        }
      >
        <div className="flex flex-wrap gap-2">
          {showMfaLink && onOpenMfaTab && (
            <Button type="button" variant="secondary" size="sm" onClick={onOpenMfaTab}>
              Manage two-factor
            </Button>
          )}
          {onOpenSignInsTab && (
            <Button type="button" variant="ghost" size="sm" onClick={onOpenSignInsTab}>
              View sign-in history
            </Button>
          )}
        </div>
      </SecuritySection>
    </div>
  );
}
