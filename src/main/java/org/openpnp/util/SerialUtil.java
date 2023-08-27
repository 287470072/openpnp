package org.openpnp.util;


import org.openpnp.model.Serial;

/**
 * 序列号工具类
 * @author yuanzf
 *
 */
public class SerialUtil {
    /**
     * 根据私钥、MAC地址、有效期生成序列号
     * @param serial 需有privateKey、mac、effectiveEndTime(13位时间戳)值
     * @return serial 含有privateKey、mac、effectiveEndTime、serialNumber值
     * @throws Exception
     */
    public static Serial generateSerialNumber(Serial serial) {
        RSAUtil rsa = new RSAUtil(null,serial.getPrivateKey());
        String serialNumber = rsa.encrypt(serial.getMac()+","+serial.getEffectiveEndTime());
        serial.setSerialNumber(serialNumber);
        return serial;

    }

    /**
     * 根据序列号、公钥校验序列号是否有效
     * @param serial 需有publicKey、serialNumber
     * @return serial 含有publicKey、mac、effectiveEndTime、serialNumber值
     * @throws Exception
     */
    public static Serial explainSerialNumber(Serial serial) {
        RSAUtil rsa = new RSAUtil(serial.getPublicKey(),null);
        String[] serialList = rsa.decrypt(serial.getSerialNumber()).split(",");
        serial.setMac(serialList[0]);
        serial.setEffectiveEndTime(serialList[1]);
        return serial;

    }
}
