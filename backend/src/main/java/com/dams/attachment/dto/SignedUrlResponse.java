package com.dams.attachment.dto;

/** A short-lived URL the browser can GET directly to view/download an attachment. */
public record SignedUrlResponse(String url, String filename, String contentType) {
}
