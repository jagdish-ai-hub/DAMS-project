package com.dams.myentries.controller;

import com.dams.myentries.dto.MyEntryResponse;
import com.dams.myentries.service.MyEntriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "My Entries" — everything the signed-in user has entered (today + recent), with workflow
 * badges; queried items are flagged for fix-and-resubmit.
 */
@RestController
@RequestMapping("/api/v1/my-entries")
@Tag(name = "My Entries", description = "The signed-in user's own documents")
@SecurityRequirement(name = "bearerAuth")
public class MyEntriesController {

    private final MyEntriesService myEntriesService;

    public MyEntriesController(MyEntriesService myEntriesService) {
        this.myEntriesService = myEntriesService;
    }

    @GetMapping
    @Operation(summary = "List my documents, newest first")
    public List<MyEntryResponse> list() {
        return myEntriesService.list();
    }
}
