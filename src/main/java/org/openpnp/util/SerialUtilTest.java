package org.openpnp.util;

import org.openpnp.model.Serial;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.sql.Timestamp;

/**
 * 序列号生成、验证入口类
 * @author: yuanzf
 * @date: 2022/3/16 11:37
 */
public class SerialUtilTest {
    /**
     * 测试方法
     * 生成rsa密钥对，需保存密钥对，后边生成序列号和解释序列号需要用到
     */
    @Test
    public void getKeyPairStart(){
        /*提前生成好rsa密钥对,私钥保存至服务端,公钥写入至客户端*/
        RSAUtil rsa = new RSAUtil();
        System.out.println("我是rsa公钥,请将我保存："+rsa.getPublicKey());
        System.out.println("我是rsa私钥,请将我保存："+rsa.getPrivateKey());
    }
    /**
     * 测试方法
     * 生成序列号，生成后将序列号提供至客户端进行配置
     */
    @Test
    public void generateStart(){
        /*new一个序列类,构造时传入私钥、MAC地址、有效结束时间*/
        Serial serial = new Serial("MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAId/DMsz04nlSLLSfppYTXRuw2j+H1xo2r65/SbvR4TwtI8kRL8vsSdBcYh3tqtoGZqFG044poYY6PWsOUisWqJNbbTuwqWfIJidnUpzjaQP8Z8RuTxr332AOK0H6/apziqK8kmVH/893N1R1V8fYbJorr83WPTeBCmE7fZWAXpHAgMBAAECgYAreZR9Tq+9OxhMaEW++D5B8Zg5g/BEElC9iae0amokOad1lkmInqDU26a2BtNRxCES90p/mqWzuSJmUVBABS0/Fl6zb39MoikRJ0fh5him35LJiFANI9GxxGnkbiahny0RKnMj42vr9id3s0ipXjHYuS8YXD9MLKVRNPUQCw47oQJBAMvppDmUTRPGyHQvoRpvhffrZa0dHCxVqojvfDtCxUmHWI9EdCHyYxR8OVQHDDif3ZYta2CkT6KtprD/4wV+kbsCQQCqG32qMOBkIQpyp+/7vAewxDnwi7KJFcKHh83+JgGInRtsjg4NpbRKQUHVYhABc+FyVLPHnR6iUFpIxmyGonrlAkEAq0e422i3iZoavIVZdIQi6sl+4XenN5JJqbZICtseLpISkFz2k6EvGoDyAqPc3x9hmIjUPhwmjEYC04BNKEtViwI/EfDUUB7Xi7fwYidUKDislvgbJEOXkN26ppCsKSHZB4+KVOimksnnOe2oA9lT1tNh86z7SRJJKNVQsFw9FfYNAkEAsvcLtghgOhgVafOpA2u9E0nYqOk7vA5oXTF8tONvJ5n4MKw52bacnX6qMQBmsJElxjvYd2zSd8XD0FzIthdw3g==","20-79-18-29-2C-B4","1650244811000");
        /*生成序列号,参数是引用类型，调用生成序列号方法后可直接从对象中获取序列号*/
        SerialUtil.generateSerialNumber(serial);
        /*获取序列号*/
        String serialNumber = serial.getSerialNumber();
        System.out.println("我是生成的序列号,请发送至客户端："+serialNumber);
    }
    /**
     * 测试方法
     * 校验序列号是否有效
     */
    @Test
    public void explainStart(){
        try {
            /*new一个序列类,构造时传入公钥、序列号*/
            Serial serial = new Serial("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCHfwzLM9OJ5Uiy0n6aWE10bsNo/h9caNq+uf0m70eE8LSPJES/L7EnQXGId7araBmahRtOOKaGGOj1rDlIrFqiTW207sKlnyCYnZ1Kc42kD/GfEbk8a999gDitB+v2qc4qivJJlR//PdzdUdVfH2GyaK6/N1j03gQphO32VgF6RwIDAQAB","13935EF2BE2C07D50FD7C521E682FD937FAF563C4374336F5D5388B502F7D5EBA2E63FCAAB280B6FA9040AAACC00D2FB035A1D6BAEEACDD7EF9B53A96CC0C8105CA2FCEC5071C65307544E4F4E1A785A27C74EE08A4325011A9919968C18DEB673DA9F3604830B5A54895B0B51E23657111BB0F16D47E39FD1D038DA14CD5C66");
            /*解释序列号,参数是引用类型，调用解释序列号方法后可直接从对象中获取MAC地址、有效结束时间*/
            SerialUtil.explainSerialNumber(serial);
            /*获取MAC地址*/
            String mac = serial.getMac();
            /*获取有效结束时间*/
            String effectiveEndTime = serial.getEffectiveEndTime();
            /*获取本机MAC地址*/
            String localMac = MachineCodeUtil.getThisMachineCode();
            /*获取当前时间戳*/
            long time = new Timestamp(System.currentTimeMillis()).getTime();
            /*判断有效结束时间是否大于当前时间*/
            if(mac.equals(localMac)&&Long.parseLong(effectiveEndTime)>time){
                System.out.println("当前序列号有效，可正常使用系统与访问资源");
            }else{
                System.out.println("当前序列号无效，请向厂商购买新的序列号");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}