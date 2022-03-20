package com.nekohit.neo.ozkens.config;

import com.nekohit.neo.ozkens.service.NekoinService;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.types.Hash160;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;
import java.util.Objects;

@Slf4j
@Configuration
@EnableConfigurationProperties(NeoN3Properties.class)
public class NeoN3Config {
    private final NeoN3Properties properties;

    public NeoN3Config(NeoN3Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Neow3j neow3j() {
        String nodeUrl = Objects.requireNonNull(this.properties.getNodeUrl(), "Node url cannot be null");
        log.info("Using Neo N3 node: {}", nodeUrl);
        return Neow3j.build(new HttpService(nodeUrl));
    }

    @Bean
    public NekoinService nekoinService() {
        Hash160 scriptHash = new Hash160(
                Objects.requireNonNull(this.properties.getNekoinScriptHash(), "Nekoin script hash cannot be null"));
        log.info("Nekoin script hash: 0x{}", scriptHash);
        String prefixBase64 = Objects.requireNonNull(this.properties.getAssetMapPrefixBase64(), "Asset map prefix cannot be null");
        String prefixHex = Numeric.toHexString(Base64.getDecoder().decode(prefixBase64));
        log.info("Asset map prefix is: (Base64): {}, (Hex): {}", prefixBase64, prefixHex);
        String walletWIF = Objects.requireNonNull(this.properties.getWalletWif(), "Wallet WIF cannot be null");
        Account account = Account.fromWIF(walletWIF);
        log.info("Using wallet: {}", account.getAddress());
        return new NekoinService(this.neow3j(), account, scriptHash, prefixHex);
    }
}
