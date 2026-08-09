package com.leakscanner.crypto.chain;

import com.leakscanner.crypto.Chain;
import com.leakscanner.crypto.ChainEco;
import com.leakscanner.crypto.dto.BalanceResultDto;
import com.leakscanner.crypto.rpc.Web3ConnectionPool;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.ECPoint;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public abstract class AbstractChainResolver implements IChain {

    private final Web3ConnectionPool connectionPool;

    protected AbstractChainResolver(Web3ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /**
     * Web3j client for the given RPC network, backed by a health-checked endpoint from the
     * pool. Takes an explicit {@link Chain} rather than deriving one from {@link #chain()},
     * since {@code chain()} identifies an ecosystem (ETH, BTC) that may have no RPC network
     * at all (BTC) or, in the future, more than one (e.g. a testnet).
     */
    protected Web3j web3j(Chain rpcChain) {
        return connectionPool.getConnection(rpcChain);
    }

    /** Call after a request through {@link #web3j} fails, so the pool rotates to a different endpoint. */
    protected void invalidateConnection(Chain rpcChain) {
        connectionPool.invalidate(rpcChain);
    }

    @Override
    public String resolveAddress(String pk) {
        return addressFromPrivateKey(pk);
    }

    @Override
    public BalanceResultDto retrieve(String pk) {
        String address = resolveAddress(pk);

        BigInteger balanceWei;
        try {
            balanceWei = web3j(this.chain()).ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();
        } catch (IOException e) {
            invalidateConnection(this.chain());
            throw new RuntimeException("Failed to fetch ETH balance for " + address, e);
        }

        BalanceResultDto result = new BalanceResultDto();
        result.setChain(this.chain().chain());
        result.setAddress(address);
        result.setPk(pk);
        result.setBalance(Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER));
        return result;
    }

    /**
     * Derives the Ethereum address (EIP-55 checksummed) for a private key,
     * entirely offline: pubKey = privKey * G on secp256k1, address = last 20
     * bytes of Keccak-256(pubKey).
     */
    public static String addressFromPrivateKey(String privateKeyHex) {
        String hex = privateKeyHex.startsWith("0x") ? privateKeyHex.substring(2) : privateKeyHex;
        BigInteger privateKey = new BigInteger(hex, 16);

        var curveParams = CustomNamedCurves.getByName("secp256k1");
        ECDomainParameters domain = new ECDomainParameters(
                curveParams.getCurve(), curveParams.getG(), curveParams.getN(), curveParams.getH());

        ECPoint publicPoint = domain.getG().multiply(privateKey).normalize();

        // Uncompressed public key body: 32-byte X followed by 32-byte Y (no 0x04 prefix).
        byte[] x = publicPoint.getXCoord().getEncoded();
        byte[] y = publicPoint.getYCoord().getEncoded();
        byte[] publicKeyBytes = new byte[64];
        System.arraycopy(x, 0, publicKeyBytes, 0, 32);
        System.arraycopy(y, 0, publicKeyBytes, 32, 32);

        byte[] hash = new Keccak.Digest256().digest(publicKeyBytes);

        byte[] addressBytes = new byte[20];
        System.arraycopy(hash, hash.length - 20, addressBytes, 0, 20);

        return "0x" + toEip55Checksum(bytesToHex(addressBytes));
    }

    /** Applies EIP-55 mixed-case checksum encoding to a lowercase hex address (no 0x prefix). */
    static String toEip55Checksum(String lowerCaseAddressHex) {
        byte[] hash = new Keccak.Digest256().digest(lowerCaseAddressHex.getBytes(StandardCharsets.US_ASCII));
        String hashHex = bytesToHex(hash);

        StringBuilder checksummed = new StringBuilder();
        for (int i = 0; i < lowerCaseAddressHex.length(); i++) {
            char c = lowerCaseAddressHex.charAt(i);
            if (Character.isDigit(c)) {
                checksummed.append(c);
            } else {
                int nibble = Character.digit(hashHex.charAt(i), 16);
                checksummed.append(nibble >= 8 ? Character.toUpperCase(c) : c);
            }
        }
        return checksummed.toString();
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
