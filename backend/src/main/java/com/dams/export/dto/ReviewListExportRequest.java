package com.dams.export.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The Accountant's "Pending & closed" window export (rev 67): the document type and the ids
 * the window is showing, in on-screen order.
 */
public record ReviewListExportRequest(
    @NotBlank(message = "List type is required")
    String type,

    @NotEmpty(message = "Nothing to export — the list is empty")
    @Size(max = 10000, message = "Too many rows to export at once — narrow the filters first")
    List<Long> ids
) {}
