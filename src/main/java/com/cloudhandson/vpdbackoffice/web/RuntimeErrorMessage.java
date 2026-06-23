package com.cloudhandson.vpdbackoffice.web;

public record RuntimeErrorMessage(
    String title,
    String message,
    boolean showSupportCommand
) {
}
