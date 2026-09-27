package com.college.placement.messaging.email;

public enum MockFailureMode {
    NONE,
    AUTH,
    RATE_LIMIT,
    SERVER_ERROR,
    TIMEOUT
}