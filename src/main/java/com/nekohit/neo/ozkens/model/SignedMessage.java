package com.nekohit.neo.ozkens.model;

public record SignedMessage(
        String message,
        // If other SDK cannot have this header
        // then we still need a public key to verify the signature
        byte header,
        String signature,
        String publicKey
) {
}
