/** Hover help text for Verification & OTP settings. */

export const VERIFICATION_SETUP_GUIDE = {
  title: "How to configure verification & OTP",
  steps: [
    "Enable **For applications → Verification & OTP** (and **SMS provider** if you need phone codes).",
    "Open **Authentication → Verification & OTP** to configure signup, login, and password recovery policies.",
    "Set up **SMS provider** (log for dev, Twilio/AWS/MSG91 for production) when using phone OTP.",
    "Under **Authentication → Feature flags**, enable **Self Registration**, **Self-service Recovery**, and **Recovery OTP** as needed.",
    "Under **Authentication → MFA**, enable **Email OTP** and/or **SMS OTP** for passwordless login.",
    "Test with hosted pages: forgot-password, signup, or your app calling the public API.",
  ],
};

export const SIGNUP_HELP = {
  section:
    "Controls how new users confirm their account after signup. Clients read this from GET /applications/{id}/signup.",
  defaultMode:
    "Link sends a verification URL by email. OTP sends a numeric code by email or SMS. This is used when the client does not specify a mode.",
  allowedModes:
    "Which modes your app may request at signup. Uncheck OTP if you only want email links.",
  requiredChannels:
    "Channels that must be verified. Email is typical; add phone when collecting mobile numbers at signup.",
  allowUserChannelChoice:
    "When on, signup API accepts verificationChannel (email or phone) so users pick how they receive the code.",
  otpLength: "Number of digits in codes (4–8). Shown in emails/SMS and validated on verify.",
  otpTtlSeconds: "Seconds before a code expires. Default 600 = 10 minutes.",
  maxAttempts: "Failed verify attempts before the challenge is invalidated.",
  resendCooldownSeconds: "Minimum wait between sending another code to the same user.",
};

export const LOGIN_HELP = {
  section:
    "Controls passwordless login options. Auth-method toggles (Email OTP / SMS OTP under Authentication → MFA) must also be enabled.",
  identifierTypes:
    "Whether users can sign in with email, phone, or both on POST .../auth/otp/request and session login.",
  passwordlessModes:
    "email_otp / phone_otp: one-time codes. magic_link: email link sign-in. Disabled modes are rejected by the API.",
  otpLength: "Digits for login OTP codes (can differ from signup).",
  otpTtlSeconds: "How long login codes remain valid.",
  maxAttempts: "Wrong-code limit before requesting a new challenge.",
  resendCooldownSeconds: "Throttle between login OTP requests.",
};

export const RECOVERY_HELP = {
  section:
    "Forgot-password flow. Requires feature flag **Self-service Recovery**. OTP mode also needs **Recovery OTP**.",
  defaultMode:
    "Link: classic reset email with URL. OTP: user receives a code, then POST .../password/reset/otp with new password.",
  allowedModes: "Modes your forgot-password UI may offer. Keep link enabled for backward compatibility.",
  allowedChannels:
    "email: code or link to mailbox. phone: SMS OTP (needs SMS provider configured).",
  allowUserChannelChoice:
    "Let users pick email vs SMS on forgot-password when both channels are allowed.",
  requireVerifiedChannel:
    "Only send recovery OTP to email/phone already marked verified — reduces abuse of unverified numbers.",
  fallbackToLink:
    "If OTP cannot be sent (SMS off, unverified phone, etc.), automatically send a reset link instead of failing.",
  otpLength: "Digits in password-reset OTP codes.",
  otpTtlSeconds: "Expiry for recovery codes.",
  maxAttempts: "Failed entries before the user must request a new code.",
  resendCooldownSeconds: "Wait time between forgot-password requests.",
};

export const SMS_HELP = {
  section:
    "Delivers SMS OTP for signup, login, and password recovery. Platform settings apply when an app has no override.",
  enabled: "Master switch. When off, SMS OTP falls back to log provider or link (if fallback is enabled).",
  provider:
    "log: codes in auth-server log (dev). Twilio/AWS SNS/MSG91: production delivery. Each app can override under its own SMS card.",
  twilioAccountSid: "From Twilio Console → Account Info. Starts with AC…",
  twilioFromNumber: "Twilio phone number in E.164 format (e.g. +15551234567).",
  twilioAuthToken: "Twilio auth token. Stored encrypted; leave blank on save to keep the current token.",
  awsRegion: "AWS region for SNS (e.g. ap-south-1 for India).",
  awsAccessKeyId: "IAM user access key with sns:Publish permission.",
  awsSecret: "IAM secret key. Encrypted at rest; leave blank to keep existing.",
  awsSenderId: "Optional sender ID shown on supported carriers.",
  msg91SenderId: "MSG91 approved sender ID.",
  msg91TemplateId: "DLT template ID (India) or flow template for transactional SMS.",
  msg91ApiKey: "MSG91 auth key from dashboard. Encrypted at rest.",
  testPhone: "Send a test SMS after saving. Use E.164 format (+country code). Check MailHog for email; server log for log provider.",
};

export const EXPOSURE_HELP = {
  verificationPolicy:
    "When enabled, each application gets a Verification & OTP tab to customize signup, login, and password recovery policies. Platform defaults apply until overridden.",
  sms:
    "When enabled, each application can configure its own SMS provider for OTP delivery. If off, only platform SMS settings apply (or log provider in dev).",
};
