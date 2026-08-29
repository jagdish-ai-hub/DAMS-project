package com.dams.search.controller;

import com.dams.search.dto.SearchResponse;
import com.dams.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Universal search. All roles; results are branch-scoped for the job-card side (see
 * {@link SearchService}). Fewer than 2 characters returns an empty result set.
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Find a customer by name, vehicle, job card or invoice")
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search customers / vehicles / job cards (?q=)")
    public SearchResponse search(@RequestParam(name = "q", required = false) String q) {
        return searchService.search(q);
    }
}
