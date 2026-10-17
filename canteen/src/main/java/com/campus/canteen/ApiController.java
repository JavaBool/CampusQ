package com.campus.canteen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/")
public class ApiController {

    @GetMapping("/")
    public ResponseEntity<?> version() {
        return ResponseEntity.ok(
                java.util.Map.of("status", "working","version","1.0")
        );
    }
}