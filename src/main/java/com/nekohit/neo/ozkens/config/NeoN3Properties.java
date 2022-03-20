package com.nekohit.neo.ozkens.config;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "neo.n3")
public class NeoN3Properties {
    @NonNull
    private String nodeUrl;
    @NonNull
    private String nekoinScriptHash;
    @NonNull
    private String assetMapPrefixBase64;
    @NonNull
    private String walletWif;
}
