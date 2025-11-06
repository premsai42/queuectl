package com.example.queuectl.service;

import org.springframework.stereotype.Service;

@Service
public class BackoffService {
    public long delaySeconds(int base, int attempts) {
        long b = Math.max(1, base);
        long d = 1;
        for (int i = 0; i < attempts; i++) {
            if (d > (24L*3600)/b) { return 24L*3600; }
            d *= b;
        }
        return d;
    }
}
