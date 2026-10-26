package com.secureone.auth.notify;

public enum EmailTokenType {
    VERIFY_EMAIL,
    RESET_PASSWORD,
    SET_PASSWORD,
    MAGIC_LINK,
    /** Confirms a new email address (the address waits in the user's attributes as pendingEmail). */
    CHANGE_EMAIL
}
