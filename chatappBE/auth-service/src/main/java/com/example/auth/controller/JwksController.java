package com.example.auth.controller;

import com.example.auth.jwt.IKeyManager;
import com.example.auth.jwt.JwksUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final IKeyManager keyManager;

    public JwksController(IKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        List<Map<String, Object>> keys = keyManager
                .getKeysForJwks()
                .stream()
                .map(JwksUtils::toJwk)
                .toList();

        return Map.of("keys", keys);
    }
}

