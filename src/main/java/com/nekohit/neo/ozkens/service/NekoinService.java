package com.nekohit.neo.ozkens.service;

import com.nekohit.neo.ozkens.model.SignedMessage;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.crypto.Sign;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.Request;
import io.neow3j.protocol.core.Response;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class NekoinService implements AutoCloseable {
    private final Neow3j neow3j;
    private final Account account;
    private final Hash160 nekoinScripHash;
    private final String assetMapPrefixHexString;

    private final Disposable subscribeToNewBlock;
    private final AtomicLong highestBlockIndex = new AtomicLong();

    public NekoinService(Neow3j neow3j, Account account, Hash160 nekoinScripHash, String assetMapPrefixHexString) {
        this.neow3j = neow3j;
        this.account = account;
        this.nekoinScripHash = nekoinScripHash;
        this.assetMapPrefixHexString = assetMapPrefixHexString;

        try {
            this.subscribeToNewBlock = this.neow3j.subscribeToNewBlocksObservable(false)
                    .subscribe(r -> {
                        var b = r.getBlock();
                        this.highestBlockIndex.set(b.getIndex());
                        log.debug("Get new block: #{}, 0x{}", b.getIndex(), b.getHash());
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Catch all check exception and rethrow it as an unchecked exception ({@link RuntimeException}).
     */
    private <S, T extends Response<?>> T sendRequest(Request<S, T> request) {
        try {
            var result = request.send();
            if (result.hasError()) {
                throw new RuntimeException(result.getError().getMessage());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: Dump assets map at given block index
    // TODO: Get someone's balance at given block index
    // TODO: Get the balances of a list of address at given block index

    /**
     * Fetch content from a given tx hash. The tx should contain events called "WriteMessage",
     * fired by Nekoin contract. The method will fetch those events and read the data.
     * <p>
     * Return a map: (Hex string of index, byte array of content).
     */
    public Map<String, byte[]> readMessage(Hash256 txHash) {
        var resultList = this.sendRequest(this.neow3j.getApplicationLog(txHash))
                .getApplicationLog()
                .getExecutions()
                .parallelStream()
                // filter HALT executions
                .filter(e -> e.getState() == NeoVMStateType.HALT)
                .flatMap(e -> e.getNotifications().parallelStream())
                // filter Nekoin contract notifies
                .filter(n -> n.getContract().equals(this.nekoinScripHash))
                .filter(n -> n.getEventName().equals("WriteMessage"))
                .map(n -> {
                    var index = n.getState().getList().get(0).getByteArray();
                    var resp = this.sendRequest(this.neow3j.invokeFunction(
                            this.nekoinScripHash,
                            "readMessage",
                            List.of(ContractParameter.byteArray(index))
                    )).getInvocationResult();
                    if (resp.hasStateFault()) {
                        throw new RuntimeException(resp.getException());
                    }
                    var content = resp.getStack().get(0).getByteArray();
                    return Pair.of(Numeric.toHexString(index), content);
                })
                .toList();
        var resultMap = new HashMap<String, byte[]>();
        resultList.forEach(p -> resultMap.put(p.getFirst(), p.getSecond()));
        return resultMap;
    }

    /**
     * Verify the {@link SignedMessage} object and return the signer's address.
     * <p>
     * Return null means the signed message is invalid.
     */
    public String extractAddressFromSignature(SignedMessage signedMessage) {
        // check signature
        var decoder = Base64.getDecoder();
        var signature = Sign.SignatureData.fromByteArray(
                signedMessage.header(),
                decoder.decode(signedMessage.signature()));
        byte[] message = signedMessage.message().getBytes(StandardCharsets.UTF_8);
        var publicKey = new ECKeyPair.ECPublicKey(decoder.decode(signedMessage.publicKey()));
        if (!Sign.verifySignature(message, signature, publicKey)) {
            throw new IllegalArgumentException("Signature didn't match the hash or public key");
        }
        // pub key -> signature -> hash -> content, return the address
        return Account.fromPublicKey(publicKey).getAddress();
    }

    /**
     * Sign the input message using server's wallet.
     */
    public SignedMessage signMessage(String message) {
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        var keyPair = this.account.getECKeyPair();
        var signature = Sign.signMessage(content, keyPair, true);
        var encoder = Base64.getEncoder();
        return new SignedMessage(message, signature.getV(),
                encoder.encodeToString(signature.getConcatenated()),
                encoder.encodeToString(keyPair.getPublicKey().getEncoded(true)));
    }

    public String getServerWalletAddress() {
        return this.account.getAddress();
    }

    /**
     * Check if the given block is already popped.
     * Force means it request data from Node when invoked.
     */
    public boolean blockIsPresentForce(BigInteger blockIndex) {
        return blockIndex.compareTo(this.getHighestBlockIndexForce()) <= 0;
    }

    /**
     * Check if the given block is already popped.
     * It will use the internal counter, instead of requesting node.
     */
    public boolean blockIsPresent(BigInteger blockIndex) {
        return blockIndex.compareTo(this.getHighestBlockIndex()) <= 0;
    }

    /**
     * Get the highest block index.
     * Force means it request data from node when invoked.
     */
    public BigInteger getHighestBlockIndexForce() {
        return this.sendRequest(this.neow3j.getBlockCount())
                .getBlockCount().add(BigInteger.ONE.negate());
    }

    /**
     * Get the highest block index.
     * It will use the internal counter, instead of requesting node.
     */
    public BigInteger getHighestBlockIndex() {
        return BigInteger.valueOf(this.highestBlockIndex.get());
    }

    @Override
    public void close() {
        this.subscribeToNewBlock.dispose();
    }
}
