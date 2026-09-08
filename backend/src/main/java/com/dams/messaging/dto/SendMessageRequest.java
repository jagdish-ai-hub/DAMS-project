package com.dams.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Send a templated message now (variables fill the template's {{placeholders}}). */
@Getter
@Setter
@NoArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "templateCode is required")
    private String templateCode;

    @NotBlank(message = "toPhone is required")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String toPhone;

    /** Variable values by placeholder name; missing ones render empty. */
    private java.util.Map<String, String> variables;

    private String relatedType;

    private Long relatedId;
}
