package com.college.placement.messaging.email;

public class EmailSendException extends RuntimeException {

    public enum Category { AUTH, RATE_LIMIT, SERVER, TIMEOUT, INVALID_REQUEST, NOT_FOUND, OTHER }

    private final Category category;
    private final boolean permanent;

    public EmailSendException(Category category, String message) {
        this(category, message, isPermanentByDefault(category));
    }

    public EmailSendException(Category category, String message, boolean permanent) {
        super(message);
        this.category = category;
        this.permanent = permanent;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isPermanent() {
        return permanent;
    }

    private static boolean isPermanentByDefault(Category category) {
        return category == Category.AUTH
                || category == Category.INVALID_REQUEST
                || category == Category.NOT_FOUND;
    }
}