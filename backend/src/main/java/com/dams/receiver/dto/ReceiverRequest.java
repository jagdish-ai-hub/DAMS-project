package com.dams.receiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReceiverRequest {

    @NotBlank(message = "Receiver name is required")
    @Size(max = 160, message = "Receiver name must be at most 160 characters")
    private String name;

    @Size(max = 32, message = "Phone must be at most 32 characters")
    private String phone;

    /** Optional on update; ignored on create. */
    private Boolean active;
}
