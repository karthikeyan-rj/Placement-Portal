package com.college.placement.messaging.email;

public interface EmailDispatchClient {

    boolean isConfigured();

    String send(EmailDraft draft) throws EmailSendException;
}