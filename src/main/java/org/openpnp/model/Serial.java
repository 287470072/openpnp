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


    private boolean isCertification = true;

    public boolean isCertification() {
        return isCertification;
    }

    public void setCertification(boolean certification) {
        isCertification = certification;
    }
}
