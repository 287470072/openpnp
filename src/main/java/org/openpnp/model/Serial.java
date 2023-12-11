package org.openpnp.model;

import lombok.Data;

/**
 * 序列号属性实体类
 *
 * @author yuanzf
 */
@Data
public class Serial {
    /*生成序列号时使用*/
    public Serial(String privateKey, String mac, String effectiveEndTime) {
        this.privateKey = privateKey;
        this.effectiveEndTime = effectiveEndTime;
        this.mac = mac;
    }

    /*序列号解密时使用*/
    public Serial(String publicKey, String serialNumber) {
        this.publicKey = publicKey;
        this.serialNumber = serialNumber;
    }

    /*序列号*/
    private String serialNumber;
    /*公钥*/
    private String publicKey;
    /*私钥*/
    private String privateKey;
    /*有结束时间(13位时间戳)*/
    private String effectiveEndTime;
    /*MAC地址*/
    private String mac;

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getEffectiveEndTime() {
        return effectiveEndTime;
    }

    public void setEffectiveEndTime(String effectiveEndTime) {
        this.effectiveEndTime = effectiveEndTime;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    private boolean isCertification = true;

    public boolean isCertification() {
        return isCertification;
    }

    public void setCertification(boolean certification) {
        isCertification = certification;
    }
}
