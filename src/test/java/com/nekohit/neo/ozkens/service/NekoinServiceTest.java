package com.nekohit.neo.ozkens.service;

import io.neow3j.types.Hash256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

@SpringBootTest
@ActiveProfiles("test")
class NekoinServiceTest {
    @Autowired
    private NekoinService nekoinService;

    @Test
    void testReadMessage() {
        var result = this.nekoinService.readMessage(new Hash256("0x9766bd20f53e37bb11b74a29ae4d4004d3768083f206d59667d1ffdfc2a4b7e0"));
        Assertions.assertTrue(result.containsKey("0xeb4c6192871897e3525b037d9c96a55b65da54c0997f0da5ec4899e70dfd58c3"));
        var content = new String(result.get("0xeb4c6192871897e3525b037d9c96a55b65da54c0997f0da5ec4899e70dfd58c3"), StandardCharsets.UTF_8);
        Assertions.assertEquals("The test message", content);
    }

    @Test
    void testSignAndReadMessage() {
        String message = "Here is the message";
        var signed = this.nekoinService.signMessage(message);
        System.out.println(signed);
        var extractedAddress = this.nekoinService.extractAddressFromSignature(signed);
        Assertions.assertEquals(this.nekoinService.getServerWalletAddress(), extractedAddress);
    }
}
