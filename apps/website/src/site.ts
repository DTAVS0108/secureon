/**
 * Website settings read at build time. SUPPORT_EMAIL is where users ask for help and report security problems;
 * until it is set, the site asks them to contact the team that provides SecureOne to them.
 */
export const SUPPORT_EMAIL = (process.env.SUPPORT_EMAIL ?? "").trim();
