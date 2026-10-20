package com.campus.canteen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing basic health and version identification for API version 1.
 */
@RestController
@RequestMapping("/api/v1/")
public class ApiController {

    /**
     * Responds with the active status and version number of the REST API.
     *
     * @return 200 OK with status and version payload
     */
    @GetMapping("/")
    public ResponseEntity<?> version() {
        return ResponseEntity.ok(
                java.util.Map.of("status", "working","version","1.0")
        );
    }
}