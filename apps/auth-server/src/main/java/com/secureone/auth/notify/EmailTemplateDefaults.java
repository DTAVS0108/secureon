package com.secureone.auth.notify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EmailTemplateDefaults {

    private EmailTemplateDefaults() {}

    public static Map<String, Object> platformDefaults() {
        Map<String, Object> templates = new LinkedHashMap<>();
        templates.put("test", template(
                "Test email",
                "SMTP connectivity check",
                "SecureOne test notification",
                """
                Hello {{userName}},

                This is a test email from SecureOne. Your SMTP and notification delivery pipeline is configured correctly.

                Status: Email delivery is operational.

                {{customMessage}}

                This automated message confirms that your email service is ready for production notifications.""",
                htmlTest(),
                List.of("userName", "customMessage")));
        templates.put("verify_email", template(
                "Verify email",
                "Sent when a user must confirm their email address",
                "Verify your {{appName}} email",
                """
                Hello {{userName}},

                Please confirm that {{userEmail}} belongs to you. This helps protect your {{appName}} account.

                {{actionLink}}

                Security note: This verification link expires in 24 hours.

                If you did not create this account, you can safely ignore this email.""",
                htmlVerifyEmail(),
                List.of("userName", "userEmail", "actionLink", "tenantName", "appName")));
        templates.put("verify_email_otp", template(
                "Verify email (OTP)",
                "Email verification using a one-time code",
                "Your {{appName}} verification code",
                """
                Hello {{userName}},

                Your verification code is: {{otpCode}}

                This code expires in {{otpTtlMinutes}} minutes.

                If you did not create this account, you can safely ignore this email.""",
                "<p>Hello {{userName}},</p><p>Your verification code is: <strong>{{otpCode}}</strong></p><p>Expires in {{otpTtlMinutes}} minutes.</p>",
                List.of("userName", "userEmail", "otpCode", "otpTtlMinutes", "tenantName", "appName")));
        templates.put("login_otp_email", template(
                "Sign-in code",
                "Passwordless email OTP at login",
                "Your {{appName}} sign-in code",
                """
                Hello {{userName}},

                Your sign-in code is: {{otpCode}}

                This code expires in {{otpTtlMinutes}} minutes.

                If you did not request this code, you can safely ignore this email.""",
                "<p>Hello {{userName}},</p><p>Your sign-in code is: <strong>{{otpCode}}</strong></p><p>Expires in {{otpTtlMinutes}} minutes.</p>",
                List.of("userName", "userEmail", "otpCode", "otpTtlMinutes", "tenantName", "appName")));
        templates.put("password_reset_otp", template(
                "Password reset (OTP)",
                "Forgot-password using a one-time code",
                "Your {{appName}} password reset code",
                """
                Hello {{userName}},

                Your password reset code is: {{otpCode}}

                This code expires in {{otpTtlMinutes}} minutes.

                If you did not request a password reset, ignore this email or contact your administrator.""",
                "<p>Hello {{userName}},</p><p>Your password reset code is: <strong>{{otpCode}}</strong></p><p>Expires in {{otpTtlMinutes}} minutes.</p>",
                List.of("userName", "userEmail", "otpCode", "otpTtlMinutes", "tenantName", "appName")));
        templates.put("password_reset", template(
                "Password reset",
                "Forgot-password and admin-initiated reset",
                "Reset your {{appName}} password",
                """
                Hello {{userName}},

                We received a request to reset the password for {{userEmail}}.

                {{actionLink}}

                Important: This link expires in 1 hour. Your password will not change unless you complete the reset.

                If you did not request a password reset, ignore this email or contact your administrator.""",
                htmlPasswordReset(),
                List.of("userName", "userEmail", "actionLink", "tenantName", "appName")));
        templates.put("magic_link", template(
                "Magic link sign-in",
                "Passwordless one-time sign-in link",
                "Your {{appName}} sign-in link",
                """
                Hello {{userName}},

                Use this one-time link to sign in to your {{appName}} workspace. For your security, the link can only be used once.

                {{actionLink}}

                Expires: This sign-in link is valid for 1 hour.

                If you did not request this sign-in link, you can safely ignore this email.""",
                htmlMagicLink(),
                List.of("userName", "userEmail", "actionLink", "tenantName", "appName")));
        templates.put("set_password_invite", template(
                "Set password invite",
                "New user invited to set an initial password",
                "Set your {{appName}} password",
                """
                Hello {{userName}},

                Your {{appName}} account for {{tenantName}} has been created. Set your password to activate your account and access your workspace.

                {{actionLink}}

                Expires: This invitation link is valid for 1 hour.

                If you were not expecting this invitation, contact your administrator.""",
                htmlSetPasswordInvite(),
                List.of("userName", "userEmail", "actionLink", "tenantName", "appName")));
        templates.put("account_suspended", template(
                "Account suspended",
                "Sent when an administrator suspends a user account",
                "Your {{appName}} account has been suspended",
                """
                Hello {{userName}},

                Your {{appName}} account {{userEmail}} has been suspended by an administrator.

                Access paused: You will not be able to sign in until your account is reactivated.

                If you believe this is a mistake, contact your administrator for {{tenantName}}.""",
                htmlAccountSuspended(),
                List.of("userName", "userEmail", "tenantName", "appName")));
        templates.put("account_reactivated", template(
                "Account reactivated",
                "Sent when an administrator reactivates a suspended account",
                "Your {{appName}} account is active again",
                """
                Hello {{userName}},

                Your {{appName}} account {{userEmail}} has been reactivated. You can sign in again.

                Access restored: Your account is now available for normal use.

                If you still cannot access {{appName}}, contact your administrator for {{tenantName}}.""",
                htmlAccountReactivated(),
                List.of("userName", "userEmail", "tenantName", "appName")));
        templates.put("signup_account_exists", template(
                "Sign-up with an existing email",
                "Sent to the owner when someone signs up with an email that already has an account",
                "You already have a {{appName}} account",
                """
                Hello {{userName}},

                Someone tried to create a {{appName}} account with {{userEmail}}, but this email already has an account.

                If it was you, sign in, or reset your password if you have forgotten it:
                {{actionLink}}

                If this was not you, you can ignore this email. Nothing about your account was changed.""",
                htmlAccountNotice(
                        "You already have an account",
                        "Someone tried to create a {{appName}} account with <strong style=\"color:#0f172a;\">{{userEmail}}</strong>, but this email already has an account. If it was you, sign in, or reset your password if you have forgotten it.",
                        "Reset password"),
                List.of("userName", "userEmail", "actionLink", "appName")));
        templates.put("signup_phone_in_use", template(
                "Sign-up with a phone number in use",
                "Sent to the person signing up when their phone number already belongs to another account",
                "We could not create your {{appName}} account",
                """
                Hello {{userName}},

                We could not create a {{appName}} account for {{userEmail}} because the phone number you entered is already used by another account.

                Sign up again without a phone number or with a different one:
                {{actionLink}}""",
                htmlAccountNotice(
                        "We could not create your account",
                        "We could not create a {{appName}} account for <strong style=\"color:#0f172a;\">{{userEmail}}</strong> because the phone number you entered is already used by another account. Sign up again without a phone number or with a different one.",
                        "Sign up again"),
                List.of("userName", "userEmail", "actionLink", "appName")));
        templates.put("account_locked", template(
                "Account locked",
                "Sent when an account is locked after too many failed sign-in attempts",
                "Your {{appName}} account was temporarily locked",
                """
                Hello {{userName}},

                Your {{appName}} account {{userEmail}} was locked for {{lockoutMinutes}} minutes after several failed sign-in attempts.

                If this was you, wait and try again. If it was not you, someone may be guessing your password: reset it now.
                {{actionLink}}""",
                htmlAccountNotice(
                        "Your account was temporarily locked",
                        "Your {{appName}} account <strong style=\"color:#0f172a;\">{{userEmail}}</strong> was locked for {{lockoutMinutes}} minutes after several failed sign-in attempts. If this was not you, someone may be guessing your password: reset it now.",
                        "Reset password"),
                List.of("userName", "userEmail", "actionLink", "appName", "lockoutMinutes")));
        templates.put("account_security_notice", template(
                "Security change",
                "Sent when two-step verification changes: a method added or removed, backup codes used or renewed, two-step verification turned off, or an administrator reset or exemption",
                "Security change on your {{appName}} account",
                """
                Hello {{userName}},

                {{securityEvent}}

                If this was not you, change your password and review your sign-in methods now.
                {{actionLink}}""",
                htmlAccountNotice(
                        "Security change on your account",
                        "{{securityEvent}} If this was not you, change your password and review your sign-in methods now.",
                        "Review account security"),
                List.of("userName", "userEmail", "actionLink", "appName", "securityEvent")));
        templates.put("two_step_setup_reminder", template(
                "Two-step verification reminder",
                "Sent before the date by which a user must set up two-step verification",
                "Set up two-step verification for {{appName}} by {{deadline}}",
                """
                Hello {{userName}},

                {{appName}} requires two-step verification. Please set it up before {{deadline}}: after that date you will be asked to set it up when you sign in.

                It takes a minute: sign in, then follow the steps (a passkey or an authenticator app).
                {{actionLink}}""",
                htmlAccountNotice(
                        "Set up two-step verification",
                        "{{appName}} requires two-step verification. Please set it up before <strong>{{deadline}}</strong>; after that date you will be asked to set it up when you sign in.",
                        "Set it up now"),
                List.of("userName", "userEmail", "actionLink", "appName", "deadline")));
        templates.put("password_changed", template(
                "Password changed",
                "Confirmation after a successful password change",
                "Your {{appName}} password was changed",
                """
                Hello {{userName}},

                The password for your {{appName}} account {{userEmail}} was successfully updated.

                No action needed: If you made this change, your account is secure.

                Did not make this change? Contact your administrator immediately to protect your account.""",
                htmlPasswordChanged(),
                List.of("userName", "userEmail", "tenantName", "appName")));
        templates.put("password_expiring_soon", template(
                "Password expiring soon",
                "Warning before password expiry per policy",
                "Your {{appName}} password expires on {{expiryDate}}",
                """
                Hello {{userName}},

                Your {{appName}} password for {{userEmail}} will expire on {{expiryDate}}.

                To avoid being locked out, sign in and change your password before it expires, or use your application's forgot-password flow if you need help.

                If you recently changed your password, you can ignore this reminder.""",
                htmlPasswordExpiringSoon(),
                List.of("userName", "userEmail", "expiryDate", "tenantName", "appName")));
        templates.put("admin_notification", template(
                "Admin notification",
                "General platform alerts to admin recipients",
                "{{subject}}",
                "{{body}}",
                htmlAdminNotification(),
                List.of("subject", "body", "tenantName", "appName")));
        templates.put("admin_security_alert", template(
                "Security alert",
                "Failed logins, lockouts, and security events",
                "[SecureOne Security] {{subject}}",
                "{{body}}",
                htmlAdminSecurityAlert(),
                List.of("subject", "body", "tenantName", "appName")));
        return templates;
    }

    private static String htmlTest() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dfe7f3;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#0f172a;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#93c5fd;">SecureOne</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">SMTP test successful</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">This is a test email from <strong style="color:#0f172a;">SecureOne</strong>. Your SMTP and notification delivery pipeline is configured correctly.</p>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#ecfdf5;border:1px solid #bbf7d0;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#166534;"><strong>Status:</strong> Email delivery is operational.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">{{customMessage}}</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">This automated message confirms that your email service is ready for production notifications.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlVerifyEmail() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dfe7f3;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#0f172a;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#93c5fd;">{{appName}}</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Verify your email address</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">Please confirm that <strong style="color:#0f172a;">{{userEmail}}</strong> belongs to you. This helps protect your {{appName}} account and keeps your workspace secure.</p>
                            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:26px 0;">
                              <tr>
                                <td style="border-radius:10px;background-color:#2563eb;">
                                  <a href="{{actionLink}}" style="display:inline-block;padding:14px 24px;font-size:15px;font-weight:700;line-height:1;color:#ffffff;text-decoration:none;border-radius:10px;">Verify email</a>
                                </td>
                              </tr>
                            </table>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#eff6ff;border:1px solid #bfdbfe;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#1e40af;"><strong>Security note:</strong> This verification link expires in 24 hours.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If the button does not work, copy and paste this URL into your browser:</p>
                            <p style="margin:8px 0 0;font-size:13px;line-height:1.6;color:#2563eb;word-break:break-all;">{{actionLink}}</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">If you did not create this account, you can safely ignore this email.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlPasswordReset() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dfe7f3;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#111827;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#c4b5fd;">Account security</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Reset your {{appName}} password</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">We received a request to reset the password for <strong style="color:#0f172a;">{{userEmail}}</strong>. Use the secure link below to choose a new password.</p>
                            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:26px 0;">
                              <tr>
                                <td style="border-radius:10px;background-color:#4f46e5;">
                                  <a href="{{actionLink}}" style="display:inline-block;padding:14px 24px;font-size:15px;font-weight:700;line-height:1;color:#ffffff;text-decoration:none;border-radius:10px;">Reset password</a>
                                </td>
                              </tr>
                            </table>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#fff7ed;border:1px solid #fed7aa;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#9a3412;"><strong>Important:</strong> This link expires in 1 hour. Your password will not change unless you complete the reset.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If the button does not work, copy and paste this URL into your browser:</p>
                            <p style="margin:8px 0 0;font-size:13px;line-height:1.6;color:#4f46e5;word-break:break-all;">{{actionLink}}</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">If you did not request a password reset, ignore this email or contact your administrator.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlMagicLink() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dfe7f3;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#0f172a;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#86efac;">Secure sign-in</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Your {{appName}} sign-in link</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">Use this one-time link to sign in to your {{appName}} workspace. For your security, the link can only be used once.</p>
                            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:26px 0;">
                              <tr>
                                <td style="border-radius:10px;background-color:#16a34a;">
                                  <a href="{{actionLink}}" style="display:inline-block;padding:14px 24px;font-size:15px;font-weight:700;line-height:1;color:#ffffff;text-decoration:none;border-radius:10px;">Sign in securely</a>
                                </td>
                              </tr>
                            </table>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#f0fdf4;border:1px solid #bbf7d0;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#166534;"><strong>Expires:</strong> This sign-in link is valid for 1 hour.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If the button does not work, copy and paste this URL into your browser:</p>
                            <p style="margin:8px 0 0;font-size:13px;line-height:1.6;color:#16a34a;word-break:break-all;">{{actionLink}}</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">If you did not request this sign-in link, you can safely ignore this email.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlSetPasswordInvite() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dfe7f3;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#0f172a;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#facc15;">Workspace invitation</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Set your {{appName}} password</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">Your {{appName}} account for <strong style="color:#0f172a;">{{tenantName}}</strong> has been created. Set your password to activate your account and access your workspace.</p>
                            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:26px 0;">
                              <tr>
                                <td style="border-radius:10px;background-color:#0f172a;">
                                  <a href="{{actionLink}}" style="display:inline-block;padding:14px 24px;font-size:15px;font-weight:700;line-height:1;color:#ffffff;text-decoration:none;border-radius:10px;">Set password</a>
                                </td>
                              </tr>
                            </table>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#fffbeb;border:1px solid #fde68a;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#92400e;"><strong>Expires:</strong> This invitation link is valid for 1 hour.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If the button does not work, copy and paste this URL into your browser:</p>
                            <p style="margin:8px 0 0;font-size:13px;line-height:1.6;color:#0f172a;word-break:break-all;">{{actionLink}}</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">If you were not expecting this invitation, contact your administrator.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlAccountSuspended() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #fed7aa;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(154,52,18,0.10);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#9a3412;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#fed7aa;">Account status</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Your account has been suspended</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">Your {{appName}} account <strong style="color:#0f172a;">{{userEmail}}</strong> has been suspended by an administrator.</p>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#fff7ed;border:1px solid #fed7aa;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#9a3412;"><strong>Access paused:</strong> You will not be able to sign in until your account is reactivated.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If you believe this is a mistake, contact your administrator for {{tenantName}}.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#fff7ed;border-top:1px solid #fed7aa;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#9a3412;">This account status notification was sent by {{appName}}.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlAccountReactivated() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #bbf7d0;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(22,101,52,0.10);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#166534;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#bbf7d0;">Account status</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Your account is active again</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">Your {{appName}} account <strong style="color:#0f172a;">{{userEmail}}</strong> has been reactivated. You can sign in again.</p>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#ecfdf5;border:1px solid #bbf7d0;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#166534;"><strong>Access restored:</strong> Your account is now available for normal use.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If you still cannot access {{appName}}, contact your administrator for {{tenantName}}.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f0fdf4;border-top:1px solid #bbf7d0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#166534;">This account status notification was sent by {{appName}}.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlAccountNotice(String heading, String body, String buttonLabel) {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dbeafe;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(30,64,175,0.10);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#1e3a8a;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#bfdbfe;">{{appName}}</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">@@HEADING@@</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">@@BODY@@</p>
                            <p style="margin:0 0 24px;"><a href="{{actionLink}}" style="display:inline-block;padding:12px 22px;border-radius:10px;background-color:#1d4ed8;color:#ffffff;font-weight:700;text-decoration:none;">@@BUTTON@@</a></p>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If this was not you, you can ignore this email. Nothing about your account was changed.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>"""
                .replace("@@HEADING@@", heading)
                .replace("@@BODY@@", body)
                .replace("@@BUTTON@@", buttonLabel);
    }

    private static String htmlPasswordExpiringSoon() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #fde68a;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(146,64,14,0.10);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#92400e;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#fde68a;">Password policy</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Your password expires soon</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">Your {{appName}} password for <strong style="color:#0f172a;">{{userEmail}}</strong> will expire on <strong style="color:#0f172a;">{{expiryDate}}</strong>.</p>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#fffbeb;border:1px solid #fde68a;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#92400e;"><strong>Action recommended:</strong> Change your password before the expiry date to avoid being locked out.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#64748b;">If you recently changed your password, you can ignore this reminder.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#fffbeb;border-top:1px solid #fde68a;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#92400e;">This reminder was sent by {{appName}} for {{tenantName}}.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlPasswordChanged() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dfe7f3;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#0f172a;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#86efac;">Security confirmation</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">Your password was changed</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#334155;">Hello {{userName}},</p>
                            <p style="margin:0 0 22px;font-size:16px;line-height:1.65;color:#334155;">The password for your {{appName}} account <strong style="color:#0f172a;">{{userEmail}}</strong> was successfully updated.</p>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#ecfdf5;border:1px solid #bbf7d0;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#166534;"><strong>No action needed:</strong> If you made this change, your account is secure.</p>
                                </td>
                              </tr>
                            </table>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;background-color:#fef2f2;border:1px solid #fecaca;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#991b1b;"><strong>Did not make this change?</strong> Contact your administrator immediately to protect your account.</p>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">This notification was sent by {{appName}} for {{tenantName}}.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlAdminNotification() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #dfe7f3;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#0f172a;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#93c5fd;">Admin notification</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">{{subject}}</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;">
                              <tr>
                                <td style="padding:22px 24px;">
                                  <div style="font-size:15px;line-height:1.75;color:#334155;white-space:pre-line;">{{body}}</div>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">Sent by {{appName}} for {{tenantName}}.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static String htmlAdminSecurityAlert() {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="width:100%;margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background-color:#ffffff;border:1px solid #fecaca;border-radius:14px;overflow:hidden;box-shadow:0 18px 45px rgba(127,29,29,0.12);">
                        <tr>
                          <td style="padding:28px 32px;background-color:#7f1d1d;color:#ffffff;">
                            <div style="font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#fecaca;">Security alert</div>
                            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.3;font-weight:700;">{{subject}}</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 24px;background-color:#fef2f2;border:1px solid #fecaca;border-radius:12px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <p style="margin:0;font-size:14px;line-height:1.6;color:#991b1b;"><strong>Action recommended:</strong> Review this security event and confirm whether administrator action is required.</p>
                                </td>
                              </tr>
                            </table>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;">
                              <tr>
                                <td style="padding:22px 24px;">
                                  <div style="font-size:15px;line-height:1.75;color:#334155;white-space:pre-line;">{{body}}</div>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background-color:#fff7f7;border-top:1px solid #fecaca;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#7f1d1d;">Sent by {{appName}} for {{tenantName}}. If this event looks suspicious, investigate immediately.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>""";
    }

    private static Map<String, Object> template(
            String name,
            String description,
            String subject,
            String bodyText,
            String bodyHtml,
            List<String> variables) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("description", description);
        row.put("subject", subject);
        row.put("bodyText", bodyText);
        row.put("bodyHtml", bodyHtml);
        row.put("cc", List.of());
        row.put("bcc", List.of());
        row.put("enabled", true);
        row.put("variables", variables);
        return row;
    }
}
