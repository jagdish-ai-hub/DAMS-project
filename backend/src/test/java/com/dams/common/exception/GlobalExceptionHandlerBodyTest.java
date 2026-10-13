package com.dams.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerBodyTest {

    @Test
    void aMissingOrUnreadableBody_is_a400_notA500() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Required request body is missing",
            new MockHttpInputMessage(new byte[0]));

        ResponseEntity<com.dams.common.dto.ApiError> res = new GlobalExceptionHandler().handleUnreadableBody(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().getMessage()).contains("missing or is not valid JSON");
    }
}
