package org.openpnp.util;

import org.openpnp.model.Serial;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.sql.Timestamp;

/**
 * 序列号生成、验证入口类
 *
 * @author: yuanzf
 * @date: 2022/3/16 11:37
 */
public class SerialUtilTest {
    /**
     * 测试方法
     * 生成rsa密钥对，需保存密钥对，后边生成序列号和解释序列号需要用到
     */
    @Test
    public void getKeyPairStart() {
        /*提前生成好rsa密钥对,私钥保存至服务端,公钥写入至客户端*/
        RSAUtil rsa = new RSAUtil();
        System.out.println("我是rsa公钥,请将我保存：" + rsa.getPublicKey());
        System.out.println("我是rsa私钥,请将我保存：" + rsa.getPrivateKey());
    }

    /**
     * 测试方法
     * 生成序列号，生成后将序列号提供至客户端进行配置
     */
    @Test
    public void generateStart() {
        String privateKey = "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAIZNXJ9ObsxAm6VdGDQN2C32ptSuPeqDZDBcbtJ3KzaHU8/MbrmLxnjTyhQN4rhxQpSW0sspFOeecWoYeXehwXoWfphCHi6Vfyg7RviEsAH1FD5ioNPVeNb49hmlREALH2thMCUlW+Eo1rKtbla55MY9wB156SBhn5NuRWUTeKhnAgMBAAECgYBqk+n04iE7JepeiEo0xOfRUfOCw+OOv0Y6up+XlcpNM4dnWCxmQm32ZNvwnjRVekwD7szJPIjCZhJKx7FdJpiKRAZDxDFQWPW0s3jijz4pX17PBP53pLTEAO8uMKZEIHA2aQ3bF6scdhtoI5fDO4ep8BtTvtXzw8NvI6BarJ90wQJBAOos7dbqDs4ivCz2wH3+fsgM8uXRnOKmrJVicarODPZFHQQnr3PByZFM6461gr2758SXgSn47AIMAHwsLx8xMD0CQQCS0ZuPmpCWTg94m6NzQaydQaltEgRpA4DgJ6Uk2bma4EIIHKP5yqo7UHOGNZXKQLSdcJNc2Jqhihvem8xlKsFzAkBkkEbTNFCHVYNaC90+PjxTzLvC1fF5o/oZbN1DbJlEaQm87w35uA7HxzChaHFs6XTuh+GAFNXFS0IqEQ9rZcRBAkEAgxTBXqUREiD/jx7l/7FS+9P0AH1lkpyeI4NB3nTFUZGHYtavUAWxluNtQRX2dmzu1OH9r5dz92XnHAjdpDVYIQJBAIh4v014nIaHQ+myKZd8p4b2DxJydpGg8HN4pyG6fBG90IJfeJvTzNrDU8S9ifOzO8lIRi5xjfQ7l6PvfioCweU=";
        /*new一个序列类,构造时传入私钥、MAC地址、有效结束时间*/
        Serial serial = new Serial(privateKey, "2433BF4969E30E55A637F8447F668AE3", "4102415999000");
        /*生成序列号,参数是引用类型，调用生成序列号方法后可直接从对象中获取序列号*/
        SerialUtil.generateSerialNumber(serial);
        /*获取序列号*/
        String serialNumber = serial.getSerialNumber();
        System.out.println("我是生成的序列号,请发送至客户端：" + serialNumber);
    }

    /**
     * 测试方法
     * 校验序列号是否有效
     */
    @Test
    public void explainStart() {
        try {
            String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCGTVyfTm7MQJulXRg0Ddgt9qbUrj3qg2QwXG7Sdys2h1PPzG65i8Z408oUDeK4cUKUltLLKRTnnnFqGHl3ocF6Fn6YQh4ulX8oO0b4hLAB9RQ+YqDT1XjW+PYZpURACx9rYTAlJVvhKNayrW5WueTGPcAdeekgYZ+TbkVlE3ioZwIDAQAB";
            /*new一个序列类,构造时传入公钥、序列号*/
            Serial serial = new Serial(publicKey, "55122D6356EA30B399253F1726C9C6D2CA4402926C4AA30C71BB0A5F0D842AC40BB6D4B8B99B2D1CC631E1969CF3C75A0D4E085BB0548E27A3B4A70ECDC411F55729234855D4849023F5BC5A077EE6B396283FE261DC20E8DBE431599F0A508D8F1A1004FD48014BB1F678AC9D936DBB4A68A316CFE297BFA77185D9CD3B0EBF");
            /*解释序列号,参数是引用类型，调用解释序列号方法后可直接从对象中获取MAC地址、有效结束时间*/
            SerialUtil.explainSerialNumber(serial);
            /*获取MAC地址*/
            String mac = serial.getMac();
            /*获取有效结束时间*/
            String effectiveEndTime = serial.getEffectiveEndTime();
            /*获取本机MAC地址*/
            String localMac = MachineCodeUtil.getThisMachineCodeMd5();
            /*获取当前时间戳*/
            long time = new Timestamp(System.currentTimeMillis()).getTime();
            /*判断有效结束时间是否大于当前时间*/
            if (mac.equals(localMac) && Long.parseLong(effectiveEndTime) > time) {
                System.out.println("当前序列号有效，可正常使用系统与访问资源");
            } else {
                System.out.println("当前序列号无效，请向厂商购买新的序列号");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}